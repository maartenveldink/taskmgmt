package eu.poc.taskmanagement.projection.tasks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.poc.taskmanagement.model.TaskStatus;
import eu.poc.taskmanagement.model.TaskType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-model projection of a task — the query-side view.
 *
 * <p>This entity is NOT part of the Axon event store; it is a plain JPA table
 * maintained by {@code TaskProjection} in response to domain events.  It holds
 * only the latest state (not history — the audit trail covers history).
 *
 * <p>Panache active-record style is used: static finder methods live on the
 * entity class itself for conciseness.
 */
@Entity
@Table(name = "task_view")
public class TaskView extends PanacheEntityBase {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Id
    @Column(name = "task_id", nullable = false)
    public String taskId;

    @Column(nullable = false)
    public String title;

    @Column(length = 2000)
    public String description;

    /** Group currently responsible for this task; always set. */
    @Column(name = "assigned_group", nullable = false)
    public String assignedGroup;

    /** Specific user assigned to this task; null if not yet assigned to a user. */
    @Column(name = "assigned_user")
    public String assignedUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false)
    public TaskType taskType;

    @Column(name = "expected_external_users", length = 4000)
    public String expectedExternalUsersJson;

    @Column(nullable = false)
    public Instant deadline;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    // =========================================================================
    // Static finder methods (Panache active-record pattern)
    // =========================================================================

    /** Returns all tasks assigned to a specific user, with optional filters. */
    public static List<TaskView> findByUser(String userName, TaskStatus status,
                                            Instant deadlineBefore, Instant deadlineAfter,
                                            int offset, int limit) {
        return buildFilteredQuery("assignedUser = ?1", userName, status, deadlineBefore, deadlineAfter, offset, limit);
    }

    /** Returns all tasks assigned to a specific group, with optional filters. */
    public static List<TaskView> findByGroup(String groupName, TaskStatus status,
                                             Instant deadlineBefore, Instant deadlineAfter,
                                             int offset, int limit) {
        return buildFilteredQuery("assignedGroup = ?1", groupName, status, deadlineBefore, deadlineAfter, offset, limit);
    }

    /** Returns all tasks, with optional filters. */
    public static List<TaskView> findAllFiltered(TaskStatus status,
                                                  Instant deadlineBefore, Instant deadlineAfter,
                                                  int offset, int limit) {
        return buildFilteredQuery(null, null, status, deadlineBefore, deadlineAfter, offset, limit);
    }

    // -------------------------------------------------------------------------
    // Private helper — builds a JPQL where-clause from optional filter args.
    // base forms the mandatory part (e.g., "assignedUser = ?1").
    // -------------------------------------------------------------------------
    private static List<TaskView> buildFilteredQuery(String base, Object arg1,
                                                        TaskStatus status,
                                                        Instant deadlineBefore, Instant deadlineAfter,
                                                        int offset, int limit) {
        var sb = new StringBuilder();
        var params = new ArrayList<>();

        // Append caller-provided base condition
        if (base != null) {
            sb.append(base);
            params.add(arg1);
        }

        // status filter
        if (status != null) {
            if (!sb.isEmpty()) sb.append(" and ");
            params.add(status);
            sb.append("status = ?").append(params.size());
        }

        // deadlineBefore filter
        if (deadlineBefore != null) {
            if (!sb.isEmpty()) sb.append(" and ");
            params.add(deadlineBefore);
            sb.append("deadline < ?").append(params.size());
        }

        // deadlineAfter filter
        if (deadlineAfter != null) {
            if (!sb.isEmpty()) sb.append(" and ");
            params.add(deadlineAfter);
            sb.append("deadline > ?").append(params.size());
        }

        String query = sb.isEmpty() ? "order by createdAt" : sb + " order by createdAt";
        PanacheQuery<TaskView> panacheQuery = find(query, params.toArray());
        panacheQuery.range(offset, offset + limit - 1);
        return panacheQuery.list();
    }

    public void setExpectedExternalUsers(List<String> users) {
        if (users == null || users.isEmpty()) {
            this.expectedExternalUsersJson = "[]";
            return;
        }
        try {
            this.expectedExternalUsersJson = OBJECT_MAPPER.writeValueAsString(users);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize expectedExternalUsers", e);
        }
    }

    public List<String> getExpectedExternalUsers() {
        if (expectedExternalUsersJson == null || expectedExternalUsersJson.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(expectedExternalUsersJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize expectedExternalUsers", e);
        }
    }
}
