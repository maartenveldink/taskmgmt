package eu.poc.taskmanagement.projection.audittrail;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;

/**
 * Append-only audit trail entry for a single domain event on a task.
 *
 * <p>Every domain event (TaskCreated, TaskAssigned, …, TaskDeadlineExceeded)
 * produces exactly one entry.  Entries are never modified or deleted
 * (requirement AT-03).
 *
 * <p>The {@code payload} column holds a human-readable JSON summary of the
 * event's relevant fields, serialised by {@code AuditTrailProjection}.
 * For a full event replay, consult the Axon JPA event store tables directly.
 */
@Entity
@Table(name = "audit_trail", indexes = {
        @Index(name = "idx_audit_task_id", columnList = "task_id")
})
public class AuditTrailEntry extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "task_id", nullable = false)
    public String taskId;

    /** ISO-8601 timestamp embedded in the Axon event message. */
    @Column(name = "event_timestamp", nullable = false)
    public Instant eventTimestamp;

    /** Simple class name of the event, e.g. "TaskCreatedEvent". */
    @Column(name = "event_type", nullable = false, length = 100)
    public String eventType;

    /**
     * JSON summary of the event payload.
     * Truncated at 4000 characters to keep the column size bounded.
     */
    @Column(length = 4000)
    public String payload;

    /** Wall-clock time when this audit entry was written. */
    @Column(name = "recorded_at", nullable = false)
    public Instant recordedAt;

    // =========================================================================
    // Finder
    // =========================================================================

    /**
     * Returns all audit entries for a task, ordered chronologically
     * (oldest first) — requirement AT-02.
     */
    public static List<AuditTrailEntry> findByTaskId(String taskId) {
        return list("taskId = ?1 order by eventTimestamp asc, id asc", taskId);
    }
}
