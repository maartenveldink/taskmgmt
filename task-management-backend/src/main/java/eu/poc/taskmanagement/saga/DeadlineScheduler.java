package eu.poc.taskmanagement.saga;

import java.time.Instant;

/**
 * Minimal scheduling abstraction used by the process managers that replace the
 * former Axon sagas.
 *
 * <h2>Why this exists</h2>
 * Axon 5.3.1 ships no deadline/scheduling support (the {@code axon-deadline} /
 * {@code axon-quartz} modules of Axon 4 have no 5.x release).  The time-bounded
 * process logic that used to live in {@code TaskDeadlineSaga} and
 * {@code UserProvisioningCompletionSaga} is now implemented with plain
 * event-driven process managers that schedule timed call-backs through this
 * abstraction.
 *
 * <p>The abstraction keeps the process managers unit-testable: production code
 * uses {@link ExecutorDeadlineScheduler} (a {@code ScheduledExecutorService}),
 * while tests supply a deterministic fake that fires call-backs on demand.
 */
public interface DeadlineScheduler {

    /**
     * Schedules {@code task} to run once at {@code when} (or immediately if
     * {@code when} is already in the past).
     *
     * @return an opaque schedule id that can be passed to {@link #cancel(String)}
     */
    String schedule(Instant when, Runnable task);

    /**
     * Cancels a previously scheduled task.  A no-op if the task already ran or
     * the id is unknown.
     */
    void cancel(String scheduleId);
}
