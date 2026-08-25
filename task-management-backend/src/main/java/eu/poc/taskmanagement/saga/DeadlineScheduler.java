package eu.poc.taskmanagement.saga;

import java.time.Instant;

/**
 * Durable scheduling abstraction used by the process managers that replace the
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
 * <h2>Durable, typed jobs</h2>
 * Rather than scheduling an opaque {@code Runnable}, callers schedule a
 * {@link ScheduledJobType} for a task id.  The production implementation
 * ({@link PersistentDeadlineScheduler}) persists each schedule as a database row
 * and, when it fires, dispatches it to the matching {@link ScheduledJobHandler}.
 * This is what makes schedules survive a restart and be safe to run on more than
 * one node (only one node claims and runs each job).  Tests supply a deterministic
 * fake that fires jobs on demand.
 */
public interface DeadlineScheduler {

    /**
     * Schedules a job of {@code type} for {@code taskId} to run once at
     * {@code when} (or as soon as possible if {@code when} is already past).
     *
     * @return an opaque schedule id that can be passed to {@link #cancel(String)}
     */
    String schedule(Instant when, ScheduledJobType type, String taskId);

    /**
     * Cancels a previously scheduled job by its opaque id.  A no-op if the job
     * already ran or the id is unknown.
     */
    void cancel(String scheduleId);

    /**
     * Cancels every pending job of {@code type} for {@code taskId}.  Idempotent —
     * lets a stateless process manager cancel without tracking schedule ids.
     */
    void cancelAll(ScheduledJobType type, String taskId);
}
