package eu.poc.taskmanagement.saga;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import eu.poc.taskmanagement.model.TaskStatus;
import eu.poc.taskmanagement.model.event.*;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaLifecycle;
import org.axonframework.modelling.saga.StartSaga;

import java.time.Instant;

/**
 * Deadline management Saga for a single task.
 *
 * <h2>Purpose</h2>
 * Validates the Axon Saga concept as a mechanism for time-bounded process logic
 * (PoC goal).  One Saga instance is created per task and terminated when the
 * task reaches any terminal state or when the deadline fires.
 *
 * <h2>Quartz integration</h2>
 * This Saga uses Axon's {@code QuartzDeadlineManager} (configured in
 * {@code AxonConfig}) to schedule a Quartz job.  Quarkus provides the
 * underlying {@code org.quartz.Scheduler} as a CDI bean; Axon wraps it
 * transparently.
 *
 * <p>Configuration reference in {@code application.yaml}:
 * <pre>
 *   quarkus.quartz.store-type: ram      # in-memory for PoC
 *   quarkus.quartz.start-mode: forced   # start immediately at boot
 * </pre>
 *
 * <h2>Lifecycle</h2>
 * <pre>
 *   TaskCreatedEvent   → @StartSaga   → schedule Quartz job at deadline
 *   TaskCompletedEvent → @EndSaga     → cancel scheduled job (on time)
 *   TaskCancelledEvent → @EndSaga     → cancel scheduled job (terminal — no escalation)
 *   TaskRejectedEvent  → @EndSaga     → cancel scheduled job (terminal — no escalation)
 *   @DeadlineHandler   → log WARN     → publish TaskDeadlineExceededEvent → @EndSaga
 * </pre>
 *
 * <h2>Escalation behaviour</h2>
 * When the deadline fires the Saga:
 * <ol>
 *   <li>Logs a WARN using the standard SLF4J / JBoss LogManager (primary escalation action).</li>
 *   <li>Publishes a {@code TaskDeadlineExceededEvent} on the Axon event bus so that
 *       future external systems can subscribe (requirement DM-05).</li>
 *   <li>Ends itself.</li>
 * </ol>
 * If the task is already in a terminal state when the deadline fires, the Saga
 * logs an error and ends without publishing the event (requirement DM-07).
 *
 * <h2>Transience of injected fields</h2>
 * {@code DeadlineManager} and {@code EventBus} are marked {@code transient} so
 * they are excluded from Saga state serialisation.  The {@code CdiResourceInjector}
 * re-injects them each time the Saga is loaded from the {@code JpaSagaStore}.
 */
/**
 * Allow Jackson to serialise the private saga state fields without public getters.
 * The {@code transient} modifier on {@code deadlineManager} and {@code eventBus}
 * excludes them from serialisation automatically (Jackson respects {@code transient}).
 */
@Slf4j
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class TaskDeadlineSaga {

    /** Quartz deadline name — must be unique within a Saga class. */
    static final String DEADLINE_NAME = "task-deadline";

    // -------------------------------------------------------------------------
    // Injected by CdiResourceInjector when the Saga is loaded.
    // transient = excluded from JPA saga state serialisation.
    // -------------------------------------------------------------------------

    /**
     * Used to schedule and cancel Quartz deadline jobs.
     * Marked {@code transient} so it is excluded from JPA saga state serialisation.
     * Re-injected by {@code CdiResourceInjector} each time the saga is loaded.
     */
    @Inject
    transient DeadlineManager deadlineManager;

    /**
     * Used to publish {@code TaskDeadlineExceededEvent} when a deadline fires.
     * Marked {@code transient} for the same reason as {@code deadlineManager}.
     */
    @Inject
    transient EventBus eventBus;

    // -------------------------------------------------------------------------
    // Saga state — serialised by JpaSagaStore via JacksonSerializer
    // -------------------------------------------------------------------------

    private String taskId;
    private String scheduleId;   // Quartz job ID returned by deadlineManager.schedule()
    private Instant deadline;
    private TaskStatus lastKnownStatus;

    // =========================================================================
    // Saga event handlers
    // =========================================================================

    /**
     * Starts the Saga and schedules the Quartz deadline job.
     *
     * <p>The association property {@code "taskId"} links all subsequent events
     * for this task to this Saga instance.
     */
    @StartSaga
    @SagaEventHandler(associationProperty = "taskId")
    public void on(TaskCreatedEvent event) {
        this.taskId = event.taskId();
        this.deadline = event.deadline();
        this.lastKnownStatus = TaskStatus.CREATED;

        /*
         * Schedule the deadline at the exact Instant from the event.
         * Using the Instant overload (rather than a Duration computed from
         * Instant.now()) ensures that:
         *   1. Production: Quartz schedules the job at the correct wall-clock time.
         *   2. Tests: the SagaTestFixture can match the deadline to the virtual clock
         *      using expectScheduledDeadlineWithName(Instant, name) without timing jitter.
         *
         * Axon serialises the payload (TaskDeadlinePayload) into the Quartz job's
         * JobDataMap using the configured JacksonSerializer. When the job fires,
         * Axon deserialises it and routes the message to @DeadlineHandler below.
         */
        this.scheduleId = deadlineManager.schedule(
                event.deadline(),
                DEADLINE_NAME,
                new TaskDeadlinePayload(event.taskId(), event.deadline())
        );

        log.debug("Saga started for taskId={} — deadline scheduled at {}", taskId, event.deadline());
    }

    /**
     * Task completed on time — cancel the pending deadline job and end the Saga.
     */
    @EndSaga
    @SagaEventHandler(associationProperty = "taskId")
    public void on(TaskCompletedEvent event) {
        this.lastKnownStatus = TaskStatus.DONE;
        cancelScheduledDeadline();
        log.debug("Saga ended for taskId={}: task completed on time", taskId);
    }

    /**
     * Task cancelled — deadline is no longer relevant; cancel the job and end.
     * No escalation is triggered because the task reached a terminal state
     * deliberately (requirement DM-03).
     */
    @EndSaga
    @SagaEventHandler(associationProperty = "taskId")
    public void on(TaskCancelledEvent event) {
        this.lastKnownStatus = TaskStatus.CANCELLED;
        cancelScheduledDeadline();
        log.debug("Saga ended for taskId={}: task cancelled — no escalation", taskId);
    }

    /**
     * Task rejected — deadline is no longer relevant; cancel the job and end.
     * No escalation is triggered (requirement DM-03).
     */
    @EndSaga
    @SagaEventHandler(associationProperty = "taskId")
    public void on(TaskRejectedEvent event) {
        this.lastKnownStatus = TaskStatus.REJECTED;
        cancelScheduledDeadline();
        log.debug("Saga ended for taskId={}: task rejected — no escalation", taskId);
    }

    // Keep lastKnownStatus up to date so the @DeadlineHandler has accurate state.
    @SagaEventHandler(associationProperty = "taskId")
    public void on(TaskAssignedEvent event) {
        this.lastKnownStatus = TaskStatus.ASSIGNED;
    }

    @SagaEventHandler(associationProperty = "taskId")
    public void on(TaskStartedEvent event) {
        this.lastKnownStatus = TaskStatus.IN_PROGRESS;
    }

    // =========================================================================
    // Deadline Handler — fires when the Quartz job triggers
    // =========================================================================

    /**
     * Called by Axon's {@code QuartzDeadlineManager} when the scheduled job fires.
     *
     * <p>This method runs in a Quartz worker thread, outside the original command
     * transaction.  The {@code AxonConfig} transaction manager begins a new JTA
     * transaction so that the saga store update and event publication are atomic.
     *
     * <h3>Escalation steps</h3>
     * <ol>
     *   <li>Guard: if the task is already in a terminal state (e.g., completed
     *       just before the Quartz job fired), log an info message and end cleanly
     *       without publishing an event (requirement DM-07).</li>
     *   <li>Log a WARN with task ID, original deadline, and current status
     *       (requirement DM-04).</li>
     *   <li>Publish {@code TaskDeadlineExceededEvent} on the Axon event bus
     *       for future external subscribers (requirement DM-05).</li>
     *   <li>End the Saga.</li>
     * </ol>
     *
     * @param payload deserialised {@code TaskDeadlinePayload} from the Quartz job
     */
    @DeadlineHandler(deadlineName = DEADLINE_NAME)
    public void onDeadline(TaskDeadlinePayload payload) {
        // Guard: terminal state means the deadline is no longer relevant.
        if (lastKnownStatus != null && lastKnownStatus.isTerminal()) {
            log.info("Saga deadline fired for taskId={} but task is already in terminal state {}. "
                    + "No escalation triggered.", taskId, lastKnownStatus);
            SagaLifecycle.end();
            return;
        }

        /*
         * Primary escalation action: WARN log (requirement DM-04).
         * This is intentionally a standard SLF4J WARN so that it appears in
         * any log aggregation tool (e.g., ELK, Loki) without special configuration.
         */
        log.warn("DEADLINE EXCEEDED — taskId={}, deadline={}, currentStatus={}. "
                + "Task was not completed within the agreed timeframe.",
                taskId, payload.deadline(), lastKnownStatus);

        /*
         * Publish event for future external subscribers (requirement DM-05).
         * External notification systems (e.g., email, Slack) can subscribe to
         * TaskDeadlineExceededEvent without modifying this Saga.
         */
        eventBus.publish(GenericEventMessage.asEventMessage(
                new TaskDeadlineExceededEvent(taskId, payload.deadline(), lastKnownStatus)
        ));

        SagaLifecycle.end();
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private void cancelScheduledDeadline() {
        if (scheduleId != null && deadlineManager != null) {
            try {
                deadlineManager.cancelSchedule(DEADLINE_NAME, scheduleId);
                log.debug("Deadline job cancelled for taskId={}", taskId);
            } catch (Exception e) {
                // Non-fatal: the job may have already fired or been removed.
                log.warn("Could not cancel deadline job for taskId={}: {}", taskId, e.getMessage());
            }
        }
    }
}
