package eu.poc.taskmanagement.model;

import eu.poc.taskmanagement.model.command.*;
import eu.poc.taskmanagement.model.event.*;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import java.util.List;

/**
 * Task aggregate — the write-side model.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Enforce all business invariants (valid state transitions, terminal-state guards)</li>
 *   <li>Apply domain events via {@code AggregateLifecycle.apply()} so they are stored
 *       in the JPA event store and dispatched to all listeners</li>
 *   <li>Rebuild its own state from events via {@code @EventSourcingHandler} methods —
 *       the aggregate is never loaded from a snapshot table; instead Axon replays
 *       all stored events</li>
 * </ul>
 *
 * <h2>State machine</h2>
 * <pre>
 *   CREATED ──► ASSIGNED ──► IN_PROGRESS ──► DONE       (terminal)
 *                │                        └─► CANCELLED  (terminal)
 *                └──► REJECTED                           (terminal)
 *   CREATED ──► REJECTED                                 (terminal)
 *   CREATED ──► CANCELLED                                (terminal)
 * </pre>
 *
 * <h2>Design note — no-arg constructor</h2>
 * Axon requires a protected no-arg constructor to reconstruct the aggregate
 * during event replay.  Never call it directly.
 */
@Slf4j
public class TaskAggregate {

    /** Axon aggregate identifier — matches the caller-supplied correlationId. */
    @AggregateIdentifier
    private String taskId;

    private TaskStatus status;
    private TaskType taskType;
    private List<String> expectedExternalUsers;
    private String assignedGroup;
    private String assignedUser;

    // -------------------------------------------------------------------------
    // Required by Axon for aggregate reconstruction during event replay.
    // -------------------------------------------------------------------------
    protected TaskAggregate() {}

    // =========================================================================
    // Command Handlers
    // =========================================================================

    /**
     * Creates a new Task aggregate.
     *
     * <p>The {@code groupName} in the command is always non-null by the time it
     * reaches here; the REST layer substitutes the default group when the caller
     * omits it (see {@code TaskResource}).
     */
    @CommandHandler
    public TaskAggregate(CreateTaskCommand cmd) {
        log.debug("Handling CreateTaskCommand for taskId={}", cmd.taskId());
        AggregateLifecycle.apply(new TaskCreatedEvent(
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
    public void handle(AssignTaskCommand cmd) {
        requireNonTerminal();
        log.debug("Handling AssignTaskCommand: taskId={} → assignee={} ({})",
                taskId, cmd.assigneeName(), cmd.assigneeType());
        String newGroup = cmd.assigneeType() == AssigneeType.GROUP ? cmd.assigneeName() : this.assignedGroup;
        String newUser  = cmd.assigneeType() == AssigneeType.USER  ? cmd.assigneeName() : this.assignedUser;
        AggregateLifecycle.apply(new TaskAssignedEvent(
                taskId, newGroup, newUser, this.assignedGroup, this.assignedUser
        ));
    }

    /**
     * Reassigns the task without changing its status.
     * Valid in any non-terminal state.
     */
    @CommandHandler
    public void handle(ReassignTaskCommand cmd) {
        requireNonTerminal();
        log.debug("Handling ReassignTaskCommand: taskId={} → reassignee={} ({})",
                taskId, cmd.newAssigneeName(), cmd.newAssigneeType());
        String newGroup = cmd.newAssigneeType() == AssigneeType.GROUP ? cmd.newAssigneeName() : this.assignedGroup;
        String newUser  = cmd.newAssigneeType() == AssigneeType.USER  ? cmd.newAssigneeName() : this.assignedUser;
        AggregateLifecycle.apply(new TaskReassignedEvent(
                taskId, this.assignedGroup, this.assignedUser, newGroup, newUser
        ));
    }

    /**
     * Transitions the task from ASSIGNED to IN_PROGRESS.
     * Works regardless of whether the current assignee is a user or a group.
     */
    @CommandHandler
    public void handle(StartTaskCommand cmd) {
        if (status != TaskStatus.ASSIGNED) {
            throw new IllegalStateException(
                    "Cannot start task %s: expected ASSIGNED but was %s".formatted(taskId, status));
        }
        log.debug("Handling StartTaskCommand: taskId={}", taskId);
        AggregateLifecycle.apply(new TaskStartedEvent(taskId, status));
    }

    /**
     * Transitions the task from IN_PROGRESS to DONE (terminal).
     */
    @CommandHandler
    public void handle(CompleteTaskCommand cmd) {
        if (status != TaskStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Cannot complete task %s: expected IN_PROGRESS but was %s".formatted(taskId, status));
        }
        log.debug("Handling CompleteTaskCommand: taskId={}", taskId);
        AggregateLifecycle.apply(new TaskCompletedEvent(taskId, status));
    }

    /**
     * Cancels the task (terminal state CANCELLED).
     * Valid in any non-terminal state.
     */
    @CommandHandler
    public void handle(CancelTaskCommand cmd) {
        requireNonTerminal();
        log.debug("Handling CancelTaskCommand: taskId={}, reason={}", taskId, cmd.reason());
        AggregateLifecycle.apply(new TaskCancelledEvent(taskId, status, cmd.reason()));
    }

    /**
     * Rejects the task (terminal state REJECTED).
     * Only valid from CREATED or ASSIGNED status.
     */
    @CommandHandler
    public void handle(RejectTaskCommand cmd) {
        if (status != TaskStatus.CREATED && status != TaskStatus.ASSIGNED) {
            throw new IllegalStateException(
                    "Cannot reject task %s: must be CREATED or ASSIGNED but was %s".formatted(taskId, status));
        }
        log.debug("Handling RejectTaskCommand: taskId={}, reason={}", taskId, cmd.reason());
        AggregateLifecycle.apply(new TaskRejectedEvent(taskId, status, cmd.reason()));
    }

    // =========================================================================
    // Event Sourcing Handlers
    //
    // These methods rebuild the aggregate state from the event stream.
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

    // =========================================================================
    // Guards
    // =========================================================================

    /**
     * Throws if the aggregate is in a terminal state.
     * Called at the start of any command handler that must not run on a
     * completed / cancelled / rejected task.
     */
    private void requireNonTerminal() {
        if (status != null && status.isTerminal()) {
            throw new IllegalStateException(
                    "Task %s is in a terminal state (%s) and cannot be modified".formatted(taskId, status));
        }
    }
}
