package eu.poc.taskmanagement.api;

import eu.poc.taskmanagement.api.dto.AssignTaskRequest;
import eu.poc.taskmanagement.api.dto.CancelTaskRequest;
import eu.poc.taskmanagement.api.dto.CreateTaskRequest;
import eu.poc.taskmanagement.api.dto.ReassignTaskRequest;
import eu.poc.taskmanagement.api.dto.RejectTaskRequest;
import eu.poc.taskmanagement.model.TaskStatus;
import eu.poc.taskmanagement.projection.tasks.query.GetAllTasksQuery;
import eu.poc.taskmanagement.projection.audittrail.query.GetAuditTrailByTaskQuery;
import eu.poc.taskmanagement.projection.tasks.query.GetTasksByGroupQuery;
import eu.poc.taskmanagement.projection.tasks.query.GetTasksByUserQuery;
import eu.poc.taskmanagement.projection.audittrail.AuditTrailEntry;
import eu.poc.taskmanagement.projection.tasks.TaskView;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * REST API — single entry point for all task commands and queries.
 *
 * <h2>URL conventions (verb-based, requirement EH-08)</h2>
 * <pre>
 *   POST   /tasks                      CreateTaskCommand
 *   POST   /tasks/{id}/assign          AssignTaskCommand
 *   POST   /tasks/{id}/reassign        ReassignTaskCommand
 *   POST   /tasks/{id}/start           StartTaskCommand
 *   POST   /tasks/{id}/complete        CompleteTaskCommand
 *   POST   /tasks/{id}/cancel          CancelTaskCommand
 *   POST   /tasks/{id}/reject          RejectTaskCommand
 *
 *   GET    /tasks                      GetAllTasksQuery       (+ filters)
 *   GET    /tasks/user/{userName}      GetTasksByUserQuery    (+ filters)
 *   GET    /tasks/group/{groupName}    GetTasksByGroupQuery   (+ filters)
 *   GET    /tasks/{id}/audit           GetAuditTrailByTaskQuery
 * </pre>
 *
 * <h2>Response codes</h2>
 * <ul>
 *   <li>{@code 200 OK} — command accepted or query returned results</li>
 *   <li>{@code 400 Bad Request} — missing / invalid field in request body</li>
 *   <li>{@code 404 Not Found} — task ID not found (aggregate does not exist)</li>
 *   <li>{@code 409 Conflict} — invalid state transition or duplicate create</li>
 *   <li>{@code 500 Internal Server Error} — unexpected error</li>
 * </ul>
 *
 * <h2>Transaction model</h2>
 * Every command endpoint is annotated {@code @Transactional}.  This begins a
 * JTA transaction before the command is dispatched to the Axon command bus.
 * The {@code SimpleCommandBus} and {@code SubscribingEventProcessor} run
 * synchronously within that transaction, so the read model and audit trail are
 * always consistent when the HTTP response is returned.
 */
@Slf4j
@Path("/tasks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TaskResource {

    @Inject
    TaskCommandDispatcher commandDispatcher;

    @Inject
    QueryGateway queryGateway;

    /**
     * Default group name applied when a {@code CreateTaskRequest} omits
     * {@code groupName}.  Configured via {@code task.default-group} in
     * {@code application.yaml} (default: {@code "unassigned"}).
     */
    @ConfigProperty(name = "task.default-group", defaultValue = "unassigned")
    String defaultGroup;

    // =========================================================================
    // Command endpoints
    // =========================================================================

    /**
     * Creates a new task.
     *
     * <p>The {@code correlationId} in the request body is used as the Axon
     * aggregate ID.  Sending the same {@code correlationId} twice returns 409.
     */
    @POST
    @Transactional
    public Response createTask(@Valid CreateTaskRequest req) {
        log.debug("POST /tasks — correlationId={}", req.correlationId());
        return commandDispatcher.createTask(req, defaultGroup);
    }

    /** Assigns the task to a user or group (status → ASSIGNED). */
    @POST
    @Path("/{id}/assign")
    @Transactional
    public Response assignTask(@PathParam("id") @NotBlank @Size(max = 100) String id,
                               @Valid AssignTaskRequest req) {
        log.debug("POST /tasks/{}/assign — assignee={}", id, req.assigneeName());
        return commandDispatcher.assignTask(id, req);
    }

    /** Reassigns the task to a different user or group without status change. */
    @POST
    @Path("/{id}/reassign")
    @Transactional
    public Response reassignTask(@PathParam("id") @NotBlank @Size(max = 100) String id,
                                 @Valid ReassignTaskRequest req) {
        log.debug("POST /tasks/{}/reassign → {}", id, req.newAssigneeName());
        return commandDispatcher.reassignTask(id, req);
    }

    /** Moves the task from ASSIGNED to IN_PROGRESS. */
    @POST
    @Path("/{id}/start")
    @Consumes(MediaType.WILDCARD)   // no request body — override class-level @Consumes
    @Transactional
    public Response startTask(@PathParam("id") @NotBlank @Size(max = 100) String id) {
        log.debug("POST /tasks/{}/start", id);
        return commandDispatcher.startTask(id);
    }

    /** Moves the task from IN_PROGRESS to DONE (terminal). */
    @POST
    @Path("/{id}/complete")
    @Consumes(MediaType.WILDCARD)   // no request body — override class-level @Consumes
    @Transactional
    public Response completeTask(@PathParam("id") @NotBlank @Size(max = 100) String id) {
        log.debug("POST /tasks/{}/complete", id);
        return commandDispatcher.completeTask(id);
    }

    /** Cancels the task (terminal). Accepts an optional reason body. */
    @POST
    @Path("/{id}/cancel")
    @Transactional
    public Response cancelTask(@PathParam("id") @NotBlank @Size(max = 100) String id,
                               @Valid CancelTaskRequest req) {
        log.debug("POST /tasks/{}/cancel", id);
        return commandDispatcher.cancelTask(id, req);
    }

    /** Rejects the task (terminal). Accepts an optional reason body. */
    @POST
    @Path("/{id}/reject")
    @Transactional
    public Response rejectTask(@PathParam("id") @NotBlank @Size(max = 100) String id,
                               @Valid RejectTaskRequest req) {
        log.debug("POST /tasks/{}/reject", id);
        return commandDispatcher.rejectTask(id, req);
    }

    // =========================================================================
    // Query endpoints
    // =========================================================================

    /**
     * Returns all tasks.  Optional query parameters:
     * <ul>
     *   <li>{@code status} — filter by task status (e.g. {@code IN_PROGRESS})</li>
     *   <li>{@code deadlineBefore} — ISO-8601 instant upper bound</li>
     *   <li>{@code deadlineAfter}  — ISO-8601 instant lower bound</li>
     * </ul>
     */
    @GET
    public List<TaskView> getAllTasks(
            @QueryParam("status") TaskStatus status,
            @QueryParam("deadlineBefore") Instant deadlineBefore,
            @QueryParam("deadlineAfter") Instant deadlineAfter,
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("50") int limit) {

        return query(new GetAllTasksQuery(
                status, deadlineBefore, deadlineAfter,
                sanitizeOffset(offset), sanitizeLimit(limit)));
    }

    /** Returns tasks assigned to a specific user, with optional filters. */
    @GET
    @Path("/user/{userName}")
    public List<TaskView> getTasksByUser(
            @PathParam("userName") @NotBlank @Size(max = 100) String userName,
            @QueryParam("status") TaskStatus status,
            @QueryParam("deadlineBefore") Instant deadlineBefore,
            @QueryParam("deadlineAfter") Instant deadlineAfter,
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("50") int limit) {

        return query(new GetTasksByUserQuery(
                userName, status, deadlineBefore, deadlineAfter,
                sanitizeOffset(offset), sanitizeLimit(limit)));
    }

    /** Returns tasks assigned to a specific group, with optional filters. */
    @GET
    @Path("/group/{groupName}")
    public List<TaskView> getTasksByGroup(
            @PathParam("groupName") @NotBlank @Size(max = 100) String groupName,
            @QueryParam("status") TaskStatus status,
            @QueryParam("deadlineBefore") Instant deadlineBefore,
            @QueryParam("deadlineAfter") Instant deadlineAfter,
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("50") int limit) {

        return query(new GetTasksByGroupQuery(
                groupName, status, deadlineBefore, deadlineAfter,
                sanitizeOffset(offset), sanitizeLimit(limit)));
    }

    /**
     * Returns the full audit trail for a task in chronological order
     * (requirement AT-02).
     */
    @GET
    @Path("/{id}/audit")
    public List<AuditTrailEntry> getAuditTrail(@PathParam("id") @NotBlank @Size(max = 100) String id) {
        return query(new GetAuditTrailByTaskQuery(id));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @SuppressWarnings("unchecked")
    private <T> T query(Object queryMessage) {
        try {
            return (T) queryGateway
                    .query(queryMessage, ResponseTypes.multipleInstancesOf(Object.class))
                    .get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Query interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Query execution failed", e.getCause());
        }
    }

    private int sanitizeOffset(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        return offset;
    }

    private int sanitizeLimit(int limit) {
        if (limit <= 0 || limit > 200) {
            throw new IllegalArgumentException("limit must be between 1 and 200");
        }
        return limit;
    }
}
