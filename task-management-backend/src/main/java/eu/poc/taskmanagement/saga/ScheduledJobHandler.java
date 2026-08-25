package eu.poc.taskmanagement.saga;

import java.time.Instant;

/**
 * Handles a fired {@link ScheduledJob} of a particular {@link ScheduledJobType}.
 *
 * <p>Process managers implement this so the {@link PersistentDeadlineScheduler}
 * can dispatch a persisted job to the right owner after a restart or on any node,
 * without needing to have kept an in-memory {@code Runnable}.
 *
 * <h2>Transaction contract</h2>
 * {@link #execute(String, Instant)} is invoked <em>inside a fresh JTA
 * transaction</em> opened by the scheduler.  The successful deletion of the job
 * row commits in that same transaction, so the handler's work (e.g. an event-store
 * append) and the job removal are atomic: either both happen or, on failure, the
 * job is retried after its lease expires.
 */
public interface ScheduledJobHandler {

    /** The job type this handler is responsible for. */
    ScheduledJobType type();

    /**
     * Executes the timed action for {@code taskId}.
     *
     * @param taskId the aggregate/task the job relates to
     * @param fireAt the instant the job was scheduled for (for a deadline job this
     *               is the original deadline)
     */
    void execute(String taskId, Instant fireAt);
}
