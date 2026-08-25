package eu.poc.taskmanagement.saga;

import eu.poc.taskmanagement.model.TaskStatus;
import eu.poc.taskmanagement.model.command.MarkDeadlineExceededCommand;
import eu.poc.taskmanagement.model.event.TaskAssignedEvent;
import eu.poc.taskmanagement.model.event.TaskCancelledEvent;
import eu.poc.taskmanagement.model.event.TaskCompletedEvent;
import eu.poc.taskmanagement.model.event.TaskCreatedEvent;
import eu.poc.taskmanagement.model.event.TaskRejectedEvent;
import eu.poc.taskmanagement.model.event.TaskStartedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deadline management process manager for a single task.
 *
 * <h2>Purpose</h2>
 * Replaces the Axon 4 {@code TaskDeadlineSaga}.  Axon 5.3.1 has no saga or
 * deadline support, so this is a plain event-driven process manager: it reacts
 * to the same domain events as before and uses a {@link DeadlineScheduler} for
 * the time-bounded logic.
 *
 * <h2>Lifecycle</h2>
 * <pre>
 *   TaskCreatedEvent   → start   → schedule a deadline call-back at event.deadline()
 *   TaskCompletedEvent → end     → cancel scheduled call-back (completed on time)
 *   TaskCancelledEvent → end     → cancel scheduled call-back (terminal — no escalation)
 *   TaskRejectedEvent  → end     → cancel scheduled call-back (terminal — no escalation)
 *   deadline fires     → dispatch MarkDeadlineExceededCommand → entity emits
 *                        TaskDeadlineExceededEvent (guarded by authoritative state)
 * </pre>
 *
 * <h2>Escalation behaviour</h2>
 * When the deadline elapses while the task is still active, the process manager
 * dispatches a {@link MarkDeadlineExceededCommand}.  The entity then appends a
 * {@code TaskDeadlineExceededEvent} (unless it has meanwhile become terminal),
 * which the audit trail records.  Routing through the entity keeps the
 * escalation event on the task's own event stream and consistent with its
 * current state.
 *
 * <h2>State</h2>
 * Per-task process state is held in memory, matching the previous Quartz
 * RAM-job-store / in-memory H2 behaviour of this PoC.
 */
@Slf4j
@ApplicationScoped
public class TaskDeadlineProcessManager {

    private static final class DeadlineState {
        // Written on the event-processor thread, read on the scheduler thread.
        // volatile establishes the happens-before needed for safe cross-thread reads.
        private volatile Instant deadline;
        private volatile TaskStatus lastKnownStatus;
        private volatile String scheduleId;
    }

    private final Map<String, DeadlineState> states = new ConcurrentHashMap<>();

    private final DeadlineScheduler scheduler;
    private final CommandGateway commandGateway;
    private final TransactionRunner transactionRunner;

    @Inject
    public TaskDeadlineProcessManager(DeadlineScheduler scheduler,
                                      CommandGateway commandGateway,
                                      TransactionRunner transactionRunner) {
        this.scheduler = scheduler;
        this.commandGateway = commandGateway;
        this.transactionRunner = transactionRunner;
    }

    // =========================================================================
    // Event handlers
    // =========================================================================

    @EventHandler
    public void on(TaskCreatedEvent event) {
        DeadlineState state = new DeadlineState();
        state.deadline = event.deadline();
        state.lastKnownStatus = TaskStatus.CREATED;
        String taskId = event.taskId();
        state.scheduleId = scheduler.schedule(event.deadline(), () -> fireDeadline(taskId));
        states.put(taskId, state);
        log.debug("Deadline process started for taskId={} — deadline scheduled at {}", taskId, event.deadline());
    }

    @EventHandler
    public void on(TaskAssignedEvent event) {
        updateStatus(event.taskId(), TaskStatus.ASSIGNED);
    }

    @EventHandler
    public void on(TaskStartedEvent event) {
        updateStatus(event.taskId(), TaskStatus.IN_PROGRESS);
    }

    @EventHandler
    public void on(TaskCompletedEvent event) {
        end(event.taskId(), TaskStatus.DONE, "completed on time");
    }

    @EventHandler
    public void on(TaskCancelledEvent event) {
        end(event.taskId(), TaskStatus.CANCELLED, "cancelled — no escalation");
    }

    @EventHandler
    public void on(TaskRejectedEvent event) {
        end(event.taskId(), TaskStatus.REJECTED, "rejected — no escalation");
    }

    // =========================================================================
    // Deadline call-back (runs on a scheduler thread)
    // =========================================================================

    private void fireDeadline(String taskId) {
        DeadlineState state = states.get(taskId);
        if (state == null) {
            return;
        }
        if (state.lastKnownStatus != null && state.lastKnownStatus.isTerminal()) {
            log.info("Deadline fired for taskId={} but task is already terminal ({}). No escalation.",
                    taskId, state.lastKnownStatus);
            states.remove(taskId);
            return;
        }
        // Delegate to the entity, which re-checks the authoritative state before
        // emitting TaskDeadlineExceededEvent.  This runs on a scheduler thread with
        // no ambient transaction, so open a fresh transaction to ensure the
        // event-store append is committed durably (not merely published in-memory).
        transactionRunner.runInTransaction(() ->
                commandGateway.sendAndWait(new MarkDeadlineExceededCommand(taskId, state.deadline)));
        states.remove(taskId);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void updateStatus(String taskId, TaskStatus status) {
        DeadlineState state = states.get(taskId);
        if (state != null) {
            state.lastKnownStatus = status;
        }
    }

    private void end(String taskId, TaskStatus terminalStatus, String reason) {
        DeadlineState state = states.remove(taskId);
        if (state != null) {
            state.lastKnownStatus = terminalStatus;
            scheduler.cancel(state.scheduleId);
            log.debug("Deadline process ended for taskId={}: {}", taskId, reason);
        }
    }
}
