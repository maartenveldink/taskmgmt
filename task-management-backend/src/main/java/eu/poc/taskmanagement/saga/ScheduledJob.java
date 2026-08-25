package eu.poc.taskmanagement.saga;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A durable, cluster-safe scheduled call-back.
 *
 * <h2>Why this is a database row</h2>
 * The process managers need timed call-backs (fire a deadline escalation, poll a
 * user directory).  Holding those timers only in a {@code ScheduledExecutorService}
 * had two production-blocking problems:
 * <ul>
 *   <li><b>No restart recovery</b> — pending timers were lost on restart, so a task
 *       whose deadline fell during downtime would never escalate.</li>
 *   <li><b>Not cluster-safe</b> — every replica held its own in-memory timers, so
 *       running more than one instance fired each deadline/poll multiple times.</li>
 * </ul>
 *
 * <p>Persisting each timer as a row fixes both: the schedule survives a restart
 * (the {@link PersistentDeadlineScheduler} poller re-discovers due rows), and a
 * lease-based atomic claim ({@link #lockedUntil}/{@link #lockedBy}) guarantees that
 * exactly one node runs each job even when several poll concurrently.
 *
 * <p>Panache active-record style, matching {@code TaskView} / {@code ProvisioningState}.
 */
@Entity
@Table(name = "scheduled_job", indexes = @Index(name = "idx_scheduled_job_fire_at", columnList = "fire_at"))
public class ScheduledJob extends PanacheEntityBase {

    /** Opaque schedule id (UUID) — primary key, returned to callers for cancellation. */
    @Id
    @Column(name = "id", nullable = false)
    public String id;

    /** Which handler should run when this job fires. */
    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 50)
    public ScheduledJobType jobType;

    /** Aggregate/task the job relates to (payload handed to the handler). */
    @Column(name = "task_id", nullable = false)
    public String taskId;

    /** When the job is due to run (also the deadline instant for escalation jobs). */
    @Column(name = "fire_at", nullable = false)
    public Instant fireAt;

    /**
     * Lease expiry while a node is executing the job.  {@code null} (or in the past)
     * means the job is claimable.  The atomic conditional update on this column is
     * what makes claiming safe across concurrent pollers / cluster nodes.
     */
    @Column(name = "locked_until")
    public Instant lockedUntil;

    /** Identifier of the node currently holding the lease (diagnostics only). */
    @Column(name = "locked_by")
    public String lockedBy;
}
