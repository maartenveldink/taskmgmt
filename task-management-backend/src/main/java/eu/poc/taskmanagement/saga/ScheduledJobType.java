package eu.poc.taskmanagement.saga;

/**
 * Type of a durable {@link ScheduledJob}.
 *
 * <p>The value routes a fired job to the matching {@link ScheduledJobHandler}
 * (i.e. the process manager that owns that kind of timed call-back).  Persisting
 * the type — rather than an opaque {@code Runnable} — is what lets the schedule
 * survive a restart and be picked up by any node in a cluster.
 */
public enum ScheduledJobType {

    /** A task deadline elapsed; escalate via {@code MarkDeadlineExceededCommand}. */
    DEADLINE_ESCALATION,

    /** Poll the external user directory for a provisioning task. */
    PROVISIONING_POLL
}
