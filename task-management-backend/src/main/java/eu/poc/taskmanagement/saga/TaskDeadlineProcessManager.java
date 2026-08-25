package eu.poc.taskmanagement.saga;

import eu.poc.taskmanagement.model.command.MarkDeadlineExceededCommand;
import eu.poc.taskmanagement.model.event.TaskCancelledEvent;
import eu.poc.taskmanagement.model.event.TaskCompletedEvent;
import eu.poc.taskmanagement.model.event.TaskCreatedEvent;
import eu.poc.taskmanagement.model.event.TaskRejectedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;

import java.time.Instant;

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
 *   TaskCreatedEvent   → schedule a DEADLINE_ESCALATION job at event.deadline()
 *   TaskCompletedEvent → cancel the escalation job (completed on time)
 *   TaskCancelledEvent → cancel the escalation job (terminal — no escalation)
 *   TaskRejectedEvent  → cancel the escalation job (terminal — no escalation)
 *   deadline fires     → dispatch MarkDeadlineExceededCommand → entity emits
 *                        TaskDeadlineExceededEvent (guarded by authoritative state)
 * </pre>
 *
 * <h2>Stateless by design</h2>
 * The process manager keeps <em>no</em> in-memory per-task state.  The pending
 * deadline lives entirely as a durable {@link ScheduledJob} row managed by the
 * {@link PersistentDeadlineScheduler}, so it survives a restart and is claimed by
 * exactly one node in a cluster.  Cancellation is expressed as
 * {@link DeadlineScheduler#cancelAll(ScheduledJobType, String)} by
 * {@code (DEADLINE_ESCALATION, taskId)} rather than by tracking a schedule id.
 *
 * <h2>Escalation behaviour</h2>
 * When the deadline elapses while the task is still active, the process manager
 * dispatches a {@link MarkDeadlineExceededCommand}.  The entity re-checks its
 * authoritative status and only then appends a {@code TaskDeadlineExceededEvent}
 * (it no-ops if the task has meanwhile become terminal).  Because that guard lives
 * in the aggregate, this process manager no longer needs to track the last known
 * status itself — a late or duplicate fire is safely ignored by the entity.
 */
@Slf4j
@ApplicationScoped
public class TaskDeadlineProcessManager implements ScheduledJobHandler {

    private final DeadlineScheduler scheduler;
    private final CommandGateway commandGateway;

    @Inject
    public TaskDeadlineProcessManager(DeadlineScheduler scheduler,
                                      CommandGateway commandGateway) {
        this.scheduler = scheduler;
        this.commandGateway = commandGateway;
    }

    // =========================================================================
    // Event handlers (event-processor thread, within the command's JTA tx)
    // =========================================================================

    @EventHandler
    @Transactional
    public void on(TaskCreatedEvent event) {
        if (event.deadline() == null) {
            return;
        }
        scheduler.schedule(event.deadline(), ScheduledJobType.DEADLINE_ESCALATION, event.taskId());
        log.debug("Deadline process started for taskId={} — deadline scheduled at {}",
                event.taskId(), event.deadline());
    }

    @EventHandler
    @Transactional
    public void on(TaskCompletedEvent event) {
        end(event.taskId(), "completed on time");
    }

    @EventHandler
    @Transactional
    public void on(TaskCancelledEvent event) {
        end(event.taskId(), "cancelled — no escalation");
    }

    @EventHandler
    @Transactional
    public void on(TaskRejectedEvent event) {
        end(event.taskId(), "rejected — no escalation");
    }

    // =========================================================================
    // Scheduled-job handler (runs inside a fresh tx opened by the scheduler)
    // =========================================================================

    @Override
    public ScheduledJobType type() {
        return ScheduledJobType.DEADLINE_ESCALATION;
    }

    @Override
    public void execute(String taskId, Instant fireAt) {
        // Delegate to the entity, which re-checks the authoritative state before
        // emitting TaskDeadlineExceededEvent. fireAt is the original deadline the
        // job was scheduled for, so it is carried through to the escalation event.
        commandGateway.sendAndWait(new MarkDeadlineExceededCommand(taskId, fireAt));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void end(String taskId, String reason) {
        scheduler.cancelAll(ScheduledJobType.DEADLINE_ESCALATION, taskId);
        log.debug("Deadline process ended for taskId={}: {}", taskId, reason);
    }
}
