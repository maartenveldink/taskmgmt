package eu.poc.taskmanagement.api;

import eu.poc.taskmanagement.api.dto.CreateTaskRequest;
import eu.poc.taskmanagement.model.TaskStatus;
import eu.poc.taskmanagement.model.TaskType;
import eu.poc.taskmanagement.projection.audittrail.AuditTrailEntry;
import eu.poc.taskmanagement.projection.tasks.TaskView;
import eu.poc.taskmanagement.test.QueryStore;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the "create a new task" scenario.
 *
 * <p>The application boots as a full {@code @QuarkusTest} against the in-memory
 * H2 database (event store, read model and audit trail).  The <em>input</em> is
 * a {@code POST /tasks} command issued over HTTP with RestAssured; success is
 * <em>verified against the read side</em> — both the {@link TaskProjection}
 * (current-state view) and the {@link AuditTrailProjection} (event history) —
 * using AssertJ assertions.
 *
 * <p>Because Axon is configured with a synchronous {@code SubscribingEventProcessor},
 * the projections are updated within the same transaction as the command, so no
 * polling is needed after the HTTP call returns.
 */
@QuarkusTest
class CreateTaskProjectionTest {

    @Inject
    QueryStore queryStore;

    @Test
    @DisplayName("POST /tasks — new task is materialised in the TaskProjection and AuditTrail")
    void createTaskIsVisibleInProjections() throws Exception {
        String taskId = "create-projection-" + UUID.randomUUID();
        String title = "Draft quarterly report";
        String description = "Compile Q3 numbers for the board";
        String groupName = "finance-team";
        Instant deadline = Instant.now().plus(7, ChronoUnit.DAYS);

        // --- Input: dispatch the CreateTask command over HTTP ----------------
        given()
                .contentType(ContentType.JSON)
                .body(new CreateTaskRequest(
                        taskId, title, description, groupName,
                        deadline, TaskType.STANDARD, List.of()))
        .when()
                .post("/tasks")
        .then()
                .statusCode(204);

        // --- Verification 1: TaskProjection (current-state read model) -------
        List<TaskView> createdTasks = queryStore.findTasksByStatus(TaskStatus.CREATED);

        assertThat(createdTasks)
                .extracting(view -> view.taskId)
                .contains(taskId);

        TaskView view = createdTasks.stream()
                .filter(t -> taskId.equals(t.taskId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "TaskView not found for taskId=" + taskId));

        assertThat(view.title).isEqualTo(title);
        assertThat(view.description).isEqualTo(description);
        assertThat(view.assignedGroup).isEqualTo(groupName);
        assertThat(view.assignedUser).isNull();
        assertThat(view.status).isEqualTo(TaskStatus.CREATED);
        assertThat(view.taskType).isEqualTo(TaskType.STANDARD);
        assertThat(view.deadline).isEqualTo(deadline);
        assertThat(view.createdAt).isNotNull();

        // --- Verification 2: AuditTrailProjection (event history) ------------
        List<AuditTrailEntry> auditTrail = queryStore.getAuditTrail(taskId);

        assertThat(auditTrail)
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.taskId).isEqualTo(taskId);
                    assertThat(entry.eventType).isEqualTo("TaskCreatedEvent");
                    assertThat(entry.payload).contains(title);
                    assertThat(entry.eventTimestamp).isNotNull();
                    assertThat(entry.recordedAt).isNotNull();
                });
    }
}
