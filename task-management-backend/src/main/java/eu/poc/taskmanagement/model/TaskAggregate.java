package eu.poc.taskmanagement.model;

import eu.poc.taskmanagement.model.command.*;
import eu.poc.taskmanagement.model.event.*;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.eventsourcing.annotation.EventSourcedEntity;
import org.axonframework.eventsourcing.annotation.EventSourcingHandler;
import org.axonframework.eventsourcing.annotation.reflection.EntityCreator;
import org.axonframework.messaging.commandhandling.annotation.CommandHandler;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import java.util.List;

/**
 * Task entity — the write-side model (Axon 5 event-sourced entity).
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Enforce all business invariants (valid state transitions, terminal-state guards)</li>
 *   <li>Append domain events via the injected {@link EventAppender} so they are stored
 *       in the JPA event store and dispatched to all listeners</li>
 *   <li>Rebuild its own state from events via {@code @EventSourcingHandler} methods</li>
 * </ul>
 *
 * <h2>Axon 5 model</h2>
 * The Axon 4 {@code @Aggregate}/{@code @AggregateIdentifier}/{@code AggregateLifecycle.apply()}
 * model is replaced by an {@link EventSourcedEntity}.  The entity is identified by the
 * {@code tagKey = "taskId"}: every domain event carries an {@code @EventTag(key = "taskId")}
 * so the {@code EventSourcingRepository} can resolve the correct event stream when loading.
 * Commands route to the entity via {@code @TargetEntityId} on the command's {@code taskId}.
 *
 * <p>The {@link CommandHandler} for {@link CreateTaskCommand} is a <em>static</em> (creational)
 * handler: it runs when no entity exists yet and appends the first event.  All other command
 * handlers are instance methods that operate on the sourced state.
 *
 * <h2>State machine</h2>
 * <pre>
 *   CREATED ──► ASSIGNED ──► IN_PROGRESS ──► DONE       (terminal)
 *                │                        └─► CANCELLED  (terminal)
 *                └──► REJECTED                           (terminal)
 *   CREATED ──► REJECTED                                 (terminal)
 *   CREATED ──► CANCELLED                                (terminal)
 * </pre>
 */
@Slf4j
@EventSourcedEntity(tagKey = "taskId")
public class TaskAggregate {

    private String taskId;
    private TaskStatus status;
    private TaskType taskType;
    private List<String> expectedExternalUsers;
    private String assignedGroup;
    private String assignedUser;

    /**
     * Required by Axon to instantiate a blank entity before sourcing it from events.
     * Never call directly.
     */
    @EntityCreator
    public TaskAggregate() {}

    // =========================================================================
    // Command Handlers
    // =========================================================================

    /**
     * Creates a new Task (creational command handler — runs when no entity exists yet).
     *
     * <p>The {@code groupName} in the command is always non-null by the time it
     * reaches here; the REST layer substitutes the default group when the caller
     * omits it (see {@code TasksHttpResource}).
     */
    @CommandHandler
    public static void handle(CreateTaskCommand cmd, EventAppender appender) {
        log.debug("Handling CreateTaskCommand for taskId={}", cmd.taskId());
        appender.append(new TaskCreatedEvent(
                cmd.taskId(),
                cmd.title(),
                cmd.description(),
                cmd.groupName(),
                null,
                cmd.deadline(),
                cmd.taskType(),
                cmd.expectedExternalUsers()
        ));
    }

    /**
     * Assigns the task to a user or group, transitioning it to ASSIGNED.
     * Valid in any non-terminal state.
     */
    @CommandHandler
    public void handle(AssignTaskCommand cmd, EventAppender appender) {
        requireNonTerminal();
        log.debug("Handling AssignTaskCommand: taskId={} → assignee={} ({})",
                taskId, cmd.assigneeName(), cmd.assigneeType());
        String newGroup = cmd.assigneeType() == AssigneeType.GROUP ? cmd.assigneeName() : this.assignedGroup;
        String newUser  = cmd.assigneeType() == AssigneeType.USER  ? cmd.assigneeName() : this.assignedUser;
        appender.append(new TaskAssignedEvent(
                taskId, newGroup, newUser, this.assignedGroup, this.assignedUser
        ));
    }

    /**
     * Reassigns the task without changing its status.
     * Valid in any non-terminal state.
     */
    @CommandHandler
    public void handle(ReassignTaskCommand cmd, EventAppender appender) {
        requireNonTerminal();
        log.debug("Handling ReassignTaskCommand: taskId={} → reassignee={} ({})",
                taskId, cmd.newAssigneeName(), cmd.newAssigneeType());
        String newGroup = cmd.newAssigneeType() == AssigneeType.GROUP ? cmd.newAssigneeName() : this.assignedGroup;
        String newUser  = cmd.newAssigneeType() == AssigneeType.USER  ? cmd.newAssigneeName() : this.assignedUser;
        appender.append(new TaskReassignedEvent(
                taskId, this.assignedGroup, this.assignedUser, newGroup, newUser
        ));
    }

    /**
     * Transitions the task from ASSIGNED to IN_PROGRESS.
     */
    @CommandHandler
    public void handle(StartTaskCommand cmd, EventAppender appender) {
        if (status != TaskStatus.ASSIGNED) {
            throw new IllegalStateException(
                    "Cannot start task %s: expected ASSIGNED but was %s".formatted(taskId, status));
        }
        log.debug("Handling StartTaskCommand: taskId={}", taskId);
        appender.append(new TaskStartedEvent(taskId, status));
    }

    /**
     * Transitions the task from IN_PROGRESS to DONE (terminal).
     */
    @CommandHandler
    public void handle(CompleteTaskCommand cmd, EventAppender appender) {
        if (status != TaskStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Cannot complete task %s: expected IN_PROGRESS but was %s".formatted(taskId, status));
        }
        log.debug("Handling CompleteTaskCommand: taskId={}", taskId);
        appender.append(new TaskCompletedEvent(taskId, status));
    }

    /**
     * Cancels the task (terminal state CANCELLED).
     * Valid in any non-terminal state.
     */
    @CommandHandler
    public void handle(CancelTaskCommand cmd, EventAppender appender) {
        requireNonTerminal();
        log.debug("Handling CancelTaskCommand: taskId={}, reason={}", taskId, cmd.reason());
        appender.append(new TaskCancelledEvent(taskId, status, cmd.reason()));
    }

    /**
     * Rejects the task (terminal state REJECTED).
     * Only valid from CREATED or ASSIGNED status.
     */
    @CommandHandler
    public void handle(RejectTaskCommand cmd, EventAppender appender) {
        if (status != TaskStatus.CREATED && status != TaskStatus.ASSIGNED) {
            throw new IllegalStateException(
                    "Cannot reject task %s: must be CREATED or ASSIGNED but was %s".formatted(taskId, status));
        }
        log.debug("Handling RejectTaskCommand: taskId={}, reason={}", taskId, cmd.reason());
        appender.append(new TaskRejectedEvent(taskId, status, cmd.reason()));
    }

    /**
     * Records that the task's deadline elapsed while it was still active.
     *
     * <p>Issued by {@code TaskDeadlineProcessManager}.  If the task has already
     * reached a terminal state (e.g. it completed just before the deadline
     * scheduler fired) no event is appended — escalation is only relevant for
     * active tasks.
     */
    @CommandHandler
    public void handle(MarkDeadlineExceededCommand cmd, EventAppender appender) {
        if (status != null && status.isTerminal()) {
            log.info("Deadline elapsed for taskId={} but task is already terminal ({}). No escalation.",
                    taskId, status);
            return;
        }
        log.warn("DEADLINE EXCEEDED — taskId={}, deadline={}, currentStatus={}. "
                + "Task was not completed within the agreed timeframe.", taskId, cmd.deadline(), status);
        appender.append(new TaskDeadlineExceededEvent(taskId, cmd.deadline(), status));
    }

    // =========================================================================
    // Event Sourcing Handlers
    //
    // These methods rebuild the entity state from the event stream.
    // They must NOT trigger any side effects — only update internal fields.
    // =========================================================================

    @EventSourcingHandler
    public void on(TaskCreatedEvent event) {
        this.taskId = event.taskId();
        this.status = TaskStatus.CREATED;
        this.taskType = event.taskType() != null ? event.taskType() : TaskType.STANDARD;
        this.expectedExternalUsers = event.expectedExternalUsers() != null
                ? List.copyOf(event.expectedExternalUsers())
                : List.of();
        this.assignedGroup = event.assignedGroup();
        this.assignedUser = event.assignedUser();
    }

    @EventSourcingHandler
    public void on(TaskAssignedEvent event) {
        this.status = TaskStatus.ASSIGNED;
        this.assignedGroup = event.assignedGroup();
        this.assignedUser = event.assignedUser();
    }

    @EventSourcingHandler
    public void on(TaskReassignedEvent event) {
        // Status does not change on reassignment.
        this.assignedGroup = event.assignedGroup();
        this.assignedUser = event.assignedUser();
    }

    @EventSourcingHandler
    public void on(TaskStartedEvent event) {
        this.status = TaskStatus.IN_PROGRESS;
    }

    @EventSourcingHandler
    public void on(TaskCompletedEvent event) {
        this.status = TaskStatus.DONE;
    }

    @EventSourcingHandler
    public void on(TaskCancelledEvent event) {
        this.status = TaskStatus.CANCELLED;
    }

    @EventSourcingHandler
    public void on(TaskRejectedEvent event) {
        this.status = TaskStatus.REJECTED;
    }

    @EventSourcingHandler
    public void on(TaskDeadlineExceededEvent event) {
        // No state change — the task keeps its current status.
        // Recorded on the stream for the audit trail only.
    }

    // =========================================================================
    // Guards
    // =========================================================================

    /**
     * Throws if the entity is in a terminal state.
     */
    private void requireNonTerminal() {
        if (status != null && status.isTerminal()) {
            throw new IllegalStateException(
                    "Task %s is in a terminal state (%s) and cannot be modified".formatted(taskId, status));
        }
    }
}
