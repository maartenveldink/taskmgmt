package eu.poc.taskmanagement.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.poc.taskmanagement.model.TaskStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Durable per-task state of the user-provisioning completion process.
 *
 * <h2>Why this is a JPA entity (not in-memory)</h2>
 * The process state is written on the Axon event-processor thread (in reaction to
 * domain events) and read/acted-on from the {@code ScheduledExecutorService}
 * scheduler thread.  Holding it in a plain {@code ConcurrentHashMap} of mutable
 * objects provided no real isolation between those threads and lost all state on
 * restart.  Persisting each task's state as a transactional database row instead:
 * <ul>
 *   <li>makes every read/write happen inside a JTA transaction, so the database
 *       provides the isolation and happens-before guarantees;</li>
 *   <li>adds optimistic locking (see {@link #version}) so a lost update between
 *       the scheduler thread and the event-processor thread is detected rather
 *       than silently applied;</li>
 *   <li>survives a restart (the row remains; recovery scheduling is out of scope
 *       for this PoC — see the readiness review).</li>
 * </ul>
 *
 * <p>Panache active-record style is used, matching {@code TaskView}.
 */
@Entity
@Table(name = "provisioning_state")
public class ProvisioningState extends PanacheEntityBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Aggregate/task identifier — primary key. */
    @Id
    @Column(name = "task_id", nullable = false)
    public String taskId;

    /** Deadline after which provisioning is considered timed out. */
    @Column(nullable = false)
    public Instant deadline;

    /** Last task status observed on the event stream; drives poll/terminal logic. */
    @Enumerated(EnumType.STRING)
    @Column(name = "last_known_status", nullable = false)
    public TaskStatus lastKnownStatus;

    /** JSON-encoded set of external users that must exist before completion. */
    @Column(name = "expected_users", length = 4000, nullable = false)
    public String expectedUsersJson;

    /** Id of the currently scheduled poll call-back (in-memory scheduler), if any. */
    @Column(name = "schedule_id")
    public String scheduleId;

    /** Optimistic-lock version — guards against lost updates across threads. */
    @Version
    @Column(nullable = false)
    public long version;

    // =========================================================================
    // expectedUsers (JSON) accessors
    // =========================================================================

    public void setExpectedUsers(Set<String> users) {
        if (users == null || users.isEmpty()) {
            this.expectedUsersJson = "[]";
            return;
        }
        try {
            this.expectedUsersJson = OBJECT_MAPPER.writeValueAsString(users);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize expectedUsers", e);
        }
    }

    public Set<String> getExpectedUsers() {
        if (expectedUsersJson == null || expectedUsersJson.isBlank()) {
            return Set.of();
        }
        try {
            return OBJECT_MAPPER.readValue(expectedUsersJson, new TypeReference<LinkedHashSet<String>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize expectedUsers", e);
        }
    }
}
