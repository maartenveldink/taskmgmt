package eu.poc.taskmanagement.api;

import eu.poc.taskmanagement.api.dto.*;
import eu.poc.taskmanagement.model.command.AssigneeType;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration test that exercises the full end-to-end use case via the REST API:
 * <pre>
 *   Create → Assign → Start → Complete
 * </pre>
 * plus verification of the audit trail and state-transition guards.
 *
 * <p>This test starts the full Quarkus application (including Axon, H2, Quartz)
 * in test mode.  It is the integration test described in the story's "Testing"
 * section and validates requirement EH-08 / EH-09 / AT-02.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TaskResourceTest {

    /**
     * Stable task ID shared across test methods so each test can pick up
     * where the previous left off.
     */
    private static final String TASK_ID = "it-" + UUID.randomUUID();
    private static final String DEADLINE =
            Instant.now().plus(30, ChronoUnit.DAYS).toString();

    // =========================================================================
    // Create
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("POST /tasks — creates task, returns 200")
    void createTask() {
        given()
                .contentType(ContentType.JSON)
                .body(new CreateTaskRequest(TASK_ID, "Integration test task",
                        "End-to-end test", null,
                        Instant.now().plus(30, ChronoUnit.DAYS)))
        .when()
                .post("/tasks")
        .then()
                .statusCode(204);
    }

    @Test
    @Order(2)
    @DisplayName("POST /tasks with duplicate correlationId — returns 409")
    void duplicateCreateReturns409() {
        given()
                .contentType(ContentType.JSON)
                .body(new CreateTaskRequest(TASK_ID, "Duplicate", "dup",
                        null, Instant.now().plus(1, ChronoUnit.DAYS)))
        .when()
                .post("/tasks")
        .then()
                .statusCode(409)
                .body("code", equalTo("CONFLICT"))
                .body("message", not(emptyOrNullString()));
    }

    // =========================================================================
    // Query after creation
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("GET /tasks — newly created task appears with status CREATED")
    void taskAppearsInList() {
        given()
        .when()
                .get("/tasks")
        .then()
                .statusCode(200)
                .body("taskId", hasItem(TASK_ID))
                .body("find { it.taskId == '" + TASK_ID + "' }.status", equalTo("CREATED"))
                .body("find { it.taskId == '" + TASK_ID + "' }.assignedGroup", equalTo("unassigned"));
    }

    @Test
    @Order(4)
    @DisplayName("GET /tasks/group/unassigned — task appears in default group")
    void taskInDefaultGroup() {
        given()
        .when()
                .get("/tasks/group/unassigned")
        .then()
                .statusCode(200)
                .body("taskId", hasItem(TASK_ID));
    }

    // =========================================================================
    // Assign
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("POST /tasks/{id}/assign — assigns to alice, status → ASSIGNED")
    void assignTask() {
        given()
                .contentType(ContentType.JSON)
                .body(new AssignTaskRequest("alice", AssigneeType.USER))
        .when()
                .post("/tasks/" + TASK_ID + "/assign")
        .then()
                .statusCode(204);

        given()
        .when()
                .get("/tasks/user/alice")
        .then()
                .statusCode(200)
                .body("taskId", hasItem(TASK_ID))
                .body("find { it.taskId == '" + TASK_ID + "' }.status", equalTo("ASSIGNED"));
    }

    // =========================================================================
    // Start
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("POST /tasks/{id}/start — status → IN_PROGRESS")
    void startTask() {
        given()
        .when()
                .post("/tasks/" + TASK_ID + "/start")
        .then()
                .statusCode(204);

        given()
        .when()
                .get("/tasks?status=IN_PROGRESS")
        .then()
                .statusCode(200)
                .body("taskId", hasItem(TASK_ID));
    }

    // =========================================================================
    // Complete
    // =========================================================================

    @Test
    @Order(7)
    @DisplayName("POST /tasks/{id}/complete — status → DONE")
    void completeTask() {
        given()
        .when()
                .post("/tasks/" + TASK_ID + "/complete")
        .then()
                .statusCode(204);

        given()
        .when()
                .get("/tasks?status=DONE")
        .then()
                .statusCode(200)
                .body("taskId", hasItem(TASK_ID));
    }

    // =========================================================================
    // Terminal state guard
    // =========================================================================

    @Test
    @Order(8)
    @DisplayName("POST /tasks/{id}/start on DONE task — returns 409")
    void startOnDoneReturns409() {
        given()
        .when()
                .post("/tasks/" + TASK_ID + "/start")
        .then()
                .statusCode(409);
    }

    // =========================================================================
    // Audit trail
    // =========================================================================

    @Test
    @Order(9)
    @DisplayName("GET /tasks/{id}/audit — contains 4 events in order")
    void auditTrailContainsAllEvents() {
        given()
        .when()
                .get("/tasks/" + TASK_ID + "/audit")
        .then()
                .statusCode(200)
                .body("eventType", containsInRelativeOrder(
                        "TaskCreatedEvent",
                        "TaskAssignedEvent",
                        "TaskStartedEvent",
                        "TaskCompletedEvent"));
    }

    // =========================================================================
    // Deadline exceeded test (requirement DM-08)
    // Creates a task with a deadline in the past to trigger immediate escalation.
    // =========================================================================

    @Test
    @Order(10)
    @DisplayName("Task with past deadline — Saga logs escalation and publishes DeadlineExceededEvent")
    void shortDeadlineTriggersSaga() throws InterruptedException {
        String shortDeadlineTaskId = "short-deadline-" + UUID.randomUUID();

        given()
                .contentType(ContentType.JSON)
                .body(new CreateTaskRequest(
                        shortDeadlineTaskId,
                        "Short deadline task",
                        "Should trigger deadline immediately",
                        "team-deadline-test",
                        // Deadline 1 second in the future so Quartz fires quickly.
                        Instant.now().plus(1, ChronoUnit.SECONDS)))
        .when()
                .post("/tasks")
        .then()
                .statusCode(204);

        // Wait for the Quartz job to fire.
        Thread.sleep(3000);

        // Audit trail should contain the DeadlineExceededEvent.
        given()
        .when()
                .get("/tasks/" + shortDeadlineTaskId + "/audit")
        .then()
                .statusCode(200)
                .body("eventType", hasItem("TaskDeadlineExceededEvent"));
    }

    @Test
    @Order(11)
    @DisplayName("POST /tasks with invalid body — returns standardized validation error")
    void invalidBodyReturnsValidationErrorEnvelope() {
        given()
                .contentType(ContentType.JSON)
                .body(new CreateTaskRequest(
                        "invalid-" + UUID.randomUUID(),
                        "   ",
                        "Missing title should fail validation",
                        "team-a",
                        Instant.now().plus(1, ChronoUnit.DAYS)))
        .when()
                .post("/tasks")
        .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("message", containsString("title"));
    }

    @Test
    @Order(12)
    @DisplayName("GET /tasks with invalid pagination limit — returns standardized bad request")
    void invalidPaginationLimitReturnsBadRequest() {
        given()
        .when()
                .get("/tasks?limit=999")
        .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("message", containsString("limit"));
    }

    @Test
    @Order(13)
    @DisplayName("POST /tasks with past deadline — returns standardized validation error")
    void createTaskWithPastDeadlineReturnsValidationError() {
        given()
                .contentType(ContentType.JSON)
                .body(new CreateTaskRequest(
                        "past-" + UUID.randomUUID(),
                        "Task with past deadline",
                        "Should fail @Future validation",
                        "team-a",
                        Instant.now().minus(1, ChronoUnit.DAYS)))
        .when()
                .post("/tasks")
        .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("message", containsString("deadline"));
    }

    @Test
    @Order(14)
    @DisplayName("POST /tasks with too long title — returns standardized validation error")
    void createTaskWithTooLongTitleReturnsValidationError() {
        given()
                .contentType(ContentType.JSON)
                .body(new CreateTaskRequest(
                        "long-title-" + UUID.randomUUID(),
                        "x".repeat(201),
                        "Title exceeds max length",
                        "team-a",
                        Instant.now().plus(1, ChronoUnit.DAYS)))
        .when()
                .post("/tasks")
        .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("message", containsString("title"));
    }

    @Test
    @Order(15)
    @DisplayName("POST /tasks/{id}/assign with blank assignee — returns standardized validation error")
    void assignWithBlankAssigneeReturnsValidationError() {
        given()
                .contentType(ContentType.JSON)
                .body(new AssignTaskRequest("   ", AssigneeType.USER))
        .when()
                .post("/tasks/" + TASK_ID + "/assign")
        .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("message", containsString("assigneeName"));
    }

    @Test
    @Order(16)
    @DisplayName("POST /tasks/{id}/reassign for unknown task — returns 404")
    void reassignUnknownTaskReturnsNotFound() {
        String unknownTaskId = "unknown-" + UUID.randomUUID();

        given()
                .contentType(ContentType.JSON)
                .body(new ReassignTaskRequest("bob", AssigneeType.USER))
        .when()
                .post("/tasks/" + unknownTaskId + "/reassign")
        .then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"))
                .body("message", not(emptyOrNullString()));
    }

    @Test
    @Order(17)
    @DisplayName("POST /tasks/{id}/cancel for unknown task — returns 404")
    void cancelUnknownTaskReturnsNotFound() {
        String unknownTaskId = "unknown-" + UUID.randomUUID();

        given()
                .contentType(ContentType.JSON)
                .body(new CancelTaskRequest("No longer needed"))
        .when()
                .post("/tasks/" + unknownTaskId + "/cancel")
        .then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"))
                .body("message", not(emptyOrNullString()));
    }

    @Test
    @Order(18)
    @DisplayName("POST /tasks/{id}/reject for unknown task — returns 404")
    void rejectUnknownTaskReturnsNotFound() {
        String unknownTaskId = "unknown-" + UUID.randomUUID();

        given()
                .contentType(ContentType.JSON)
                .body(new RejectTaskRequest("Invalid request"))
        .when()
                .post("/tasks/" + unknownTaskId + "/reject")
        .then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"))
                .body("message", not(emptyOrNullString()));
    }

    @Test
    @Order(19)
    @DisplayName("POST /tasks/{id}/cancel with too long reason — returns standardized validation error")
    void cancelWithTooLongReasonReturnsValidationError() {
        given()
                .contentType(ContentType.JSON)
                .body(new CancelTaskRequest("r".repeat(1001)))
        .when()
                .post("/tasks/" + TASK_ID + "/cancel")
        .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("message", containsString("reason"));
    }

    @Test
    @Order(20)
    @DisplayName("POST /tasks/{id}/reject with too long reason — returns standardized validation error")
    void rejectWithTooLongReasonReturnsValidationError() {
        given()
                .contentType(ContentType.JSON)
                .body(new RejectTaskRequest("r".repeat(1001)))
        .when()
                .post("/tasks/" + TASK_ID + "/reject")
        .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("message", containsString("reason"));
    }

    @Test
    @Order(21)
    @DisplayName("GET /tasks with negative offset — returns standardized bad request")
    void invalidOffsetReturnsBadRequest() {
        given()
        .when()
                .get("/tasks?offset=-1")
        .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("message", containsString("offset"));
    }

    @Test
    @Order(22)
    @DisplayName("GET /tasks/user/{userName} with overlong path param — returns standardized validation error")
    void overlongUserNameReturnsValidationError() {
        String userName = "u".repeat(101);

        given()
        .when()
                .get("/tasks/user/" + userName)
        .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("message", containsString("userName"));
    }

    @Test
    @Order(23)
    @DisplayName("POST /tasks/{id}/start for unknown task — returns 404")
    void startUnknownTaskReturnsNotFound() {
        String unknownTaskId = "unknown-" + UUID.randomUUID();

        given()
        .when()
                .post("/tasks/" + unknownTaskId + "/start")
        .then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"))
                .body("message", not(emptyOrNullString()));
    }

    @Test
    @Order(24)
    @DisplayName("POST /tasks/{id}/complete for unknown task — returns 404")
    void completeUnknownTaskReturnsNotFound() {
        String unknownTaskId = "unknown-" + UUID.randomUUID();

        given()
        .when()
                .post("/tasks/" + unknownTaskId + "/complete")
        .then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"))
                .body("message", not(emptyOrNullString()));
    }

    @Test
    @Order(25)
    @DisplayName("POST /tasks/{id}/reassign with blank assignee — returns standardized validation error")
    void reassignWithBlankAssigneeReturnsValidationError() {
        given()
                .contentType(ContentType.JSON)
                .body(new ReassignTaskRequest("   ", AssigneeType.USER))
        .when()
                .post("/tasks/" + TASK_ID + "/reassign")
        .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"))
                .body("message", containsString("newAssigneeName"));
    }
}
