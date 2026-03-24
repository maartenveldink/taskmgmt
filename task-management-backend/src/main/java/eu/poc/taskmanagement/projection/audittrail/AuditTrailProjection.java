package eu.poc.taskmanagement.projection.audittrail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import eu.poc.taskmanagement.model.event.*;
import eu.poc.taskmanagement.projection.audittrail.query.GetAuditTrailByTaskQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.Timestamp;
import org.axonframework.queryhandling.QueryHandler;

import java.time.Instant;
import java.util.List;

/**
 * Audit trail projection — append-only record of all domain events.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Listen to every domain event and append one {@code AuditTrailEntry}
 *       per event to the {@code audit_trail} JPA table.</li>
 *   <li>Answer {@code GetAuditTrailByTaskQuery} queries in chronological order.</li>
 * </ul>
 *
 * <h2>Append-only guarantee</h2>
 * This projection only ever calls {@code persist()}, never {@code merge()} or
 * {@code delete()}.  Existing entries are immutable (requirement AT-03).
 *
 * <h2>Payload format</h2>
 * The {@code payload} column stores a compact JSON summary of the event's
 * key fields.  It is <em>not</em> a full event serialisation; the Axon
 * {@code DomainEventEntry} table holds the canonical serialised form.
 *
 * <h2>Replay safety</h2>
 * Because entries are append-only with auto-generated IDs, replaying events
 * will produce duplicate entries.  To replay cleanly, truncate the
 * {@code audit_trail} table first (requirement AT-07).
 */
@Slf4j
@ApplicationScoped
public class AuditTrailProjection {

    /**
     * Dedicated ObjectMapper for audit payload serialisation.
     * We intentionally keep this separate from the Quarkus / Axon ObjectMapper
     * to avoid accidentally coupling audit format to internal Axon serialisation.
     */
    private static final ObjectMapper AUDIT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // =========================================================================
    // Event Handlers
    // =========================================================================

    @EventHandler
    @Transactional
    public void on(TaskCreatedEvent event, @Timestamp Instant timestamp) {
        log.debug("Audit: TaskCreatedEvent for taskId={}", event.taskId());
        append(event.taskId(), timestamp, "TaskCreatedEvent", event);
    }

    @EventHandler
    @Transactional
    public void on(TaskAssignedEvent event, @Timestamp Instant timestamp) {
        log.debug("Audit: TaskAssignedEvent for taskId={}", event.taskId());
        append(event.taskId(), timestamp, "TaskAssignedEvent", event);
    }

    @EventHandler
    @Transactional
    public void on(TaskReassignedEvent event, @Timestamp Instant timestamp) {
        log.debug("Audit: TaskReassignedEvent for taskId={}", event.taskId());
        append(event.taskId(), timestamp, "TaskReassignedEvent", event);
    }

    @EventHandler
    @Transactional
    public void on(TaskStartedEvent event, @Timestamp Instant timestamp) {
        log.debug("Audit: TaskStartedEvent for taskId={}", event.taskId());
        append(event.taskId(), timestamp, "TaskStartedEvent", event);
    }

    @EventHandler
    @Transactional
    public void on(TaskCompletedEvent event, @Timestamp Instant timestamp) {
        log.debug("Audit: TaskCompletedEvent for taskId={}", event.taskId());
        append(event.taskId(), timestamp, "TaskCompletedEvent", event);
    }

    @EventHandler
    @Transactional
    public void on(TaskCancelledEvent event, @Timestamp Instant timestamp) {
        log.debug("Audit: TaskCancelledEvent for taskId={}", event.taskId());
        append(event.taskId(), timestamp, "TaskCancelledEvent", event);
    }

    @EventHandler
    @Transactional
    public void on(TaskRejectedEvent event, @Timestamp Instant timestamp) {
        log.debug("Audit: TaskRejectedEvent for taskId={}", event.taskId());
        append(event.taskId(), timestamp, "TaskRejectedEvent", event);
    }

    @EventHandler
    @Transactional
    public void on(TaskDeadlineExceededEvent event, @Timestamp Instant timestamp) {
        log.debug("Audit: TaskDeadlineExceededEvent for taskId={}", event.taskId());
        append(event.taskId(), timestamp, "TaskDeadlineExceededEvent", event);
    }

    // =========================================================================
    // Query Handler
    // =========================================================================

    @QueryHandler
    @Transactional
    public List<AuditTrailEntry> handle(GetAuditTrailByTaskQuery query) {
        return AuditTrailEntry.findByTaskId(query.taskId());
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private void append(String taskId, Instant eventTimestamp, String eventType, Object eventPayload) {
        AuditTrailEntry entry = new AuditTrailEntry();
        entry.taskId = taskId;
        entry.eventTimestamp = eventTimestamp;
        entry.eventType = eventType;
        entry.payload = toJson(eventPayload);
        entry.recordedAt = Instant.now();
        entry.persist();
    }

    private String toJson(Object obj) {
        try {
            String json = AUDIT_MAPPER.writeValueAsString(obj);
            // Truncate to 4000 chars to stay within the column limit.
            return json.length() > 4000 ? json.substring(0, 4000) : json;
        } catch (JsonProcessingException e) {
            log.warn("Audit: failed to serialise event payload to JSON", e);
            return "{\"error\":\"serialisation failed\"}";
        }
    }
}
