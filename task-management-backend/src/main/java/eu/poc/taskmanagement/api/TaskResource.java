package eu.poc.taskmanagement.api;

import eu.poc.taskmanagement.model.command.*;
import eu.poc.taskmanagement.api.dto.*;
import eu.poc.taskmanagement.api.error.ApiError;
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
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.modelling.command.AggregateStreamCreationException;
import org.axonframework.modelling.command.ConcurrencyException;
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
    CommandGateway commandGateway;

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
        String group = (req.groupName() != null && !req.groupName().isBlank())
                ? req.groupName() : defaultGroup;

        log.debug("POST /tasks — correlationId={}, group={}", req.correlationId(), group);

        return dispatchCommand(new CreateTaskCommand(
                req.correlationId(),
                req.title(),
                req.description(),
                group,
                req.deadline()
        ));
    }

    /** Assigns the task to a user or group (status → ASSIGNED). */
    @POST
    @Path("/{id}/assign")
    @Transactional
    public Response assignTask(@PathParam("id") String id,
                               @Valid AssignTaskRequest req) {
        log.debug("POST /tasks/{}/assign — assignee={}", id, req.assigneeName());
        return dispatchCommand(new AssignTaskCommand(id, req.assigneeName(), req.assigneeType()));
    }

    /** Reassigns the task to a different user or group without status change. */
    @POST
    @Path("/{id}/reassign")
    @Transactional
    public Response reassignTask(@PathParam("id") String id,
                                 @Valid ReassignTaskRequest req) {
        log.debug("POST /tasks/{}/reassign → {}", id, req.newAssigneeName());
        return dispatchCommand(new ReassignTaskCommand(id, req.newAssigneeName(), req.newAssigneeType()));
    }

    /** Moves the task from ASSIGNED to IN_PROGRESS. */
    @POST
    @Path("/{id}/start")
    @Consumes(MediaType.WILDCARD)   // no request body — override class-level @Consumes
    @Transactional
    public Response startTask(@PathParam("id") String id) {
        log.debug("POST /tasks/{}/start", id);
        return dispatchCommand(new StartTaskCommand(id));
    }

    /** Moves the task from IN_PROGRESS to DONE (terminal). */
    @POST
    @Path("/{id}/complete")
    @Consumes(MediaType.WILDCARD)   // no request body — override class-level @Consumes
    @Transactional
    public Response completeTask(@PathParam("id") String id) {
        log.debug("POST /tasks/{}/complete", id);
        return dispatchCommand(new CompleteTaskCommand(id));
    }

    /** Cancels the task (terminal). Accepts an optional reason body. */
    @POST
    @Path("/{id}/cancel")
    @Transactional
    public Response cancelTask(@PathParam("id") String id, CancelTaskRequest req) {
        String reason = (req != null) ? req.reason() : null;
        log.debug("POST /tasks/{}/cancel — reason={}", id, reason);
        return dispatchCommand(new CancelTaskCommand(id, reason));
    }

    /** Rejects the task (terminal). Accepts an optional reason body. */
    @POST
    @Path("/{id}/reject")
    @Transactional
    public Response rejectTask(@PathParam("id") String id, RejectTaskRequest req) {
        String reason = (req != null) ? req.reason() : null;
        log.debug("POST /tasks/{}/reject — reason={}", id, reason);
        return dispatchCommand(new RejectTaskCommand(id, reason));
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
            @QueryParam("deadlineAfter") Instant deadlineAfter) {

        return query(new GetAllTasksQuery(status, deadlineBefore, deadlineAfter));
    }

    /** Returns tasks assigned to a specific user, with optional filters. */
    @GET
    @Path("/user/{userName}")
    public List<TaskView> getTasksByUser(
            @PathParam("userName") String userName,
            @QueryParam("status") TaskStatus status,
            @QueryParam("deadlineBefore") Instant deadlineBefore,
            @QueryParam("deadlineAfter") Instant deadlineAfter) {

        return query(new GetTasksByUserQuery(userName, status, deadlineBefore, deadlineAfter));
    }

    /** Returns tasks assigned to a specific group, with optional filters. */
    @GET
    @Path("/group/{groupName}")
    public List<TaskView> getTasksByGroup(
            @PathParam("groupName") String groupName,
            @QueryParam("status") TaskStatus status,
            @QueryParam("deadlineBefore") Instant deadlineBefore,
            @QueryParam("deadlineAfter") Instant deadlineAfter) {

        return query(new GetTasksByGroupQuery(groupName, status, deadlineBefore, deadlineAfter));
    }

    /**
     * Returns the full audit trail for a task in chronological order
     * (requirement AT-02).
     */
    @GET
    @Path("/{id}/audit")
    public List<AuditTrailEntry> getAuditTrail(@PathParam("id") String id) {
        return query(new GetAuditTrailByTaskQuery(id));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Dispatches a command synchronously and maps Axon exceptions to HTTP status codes.
     *
     * <ul>
     *   <li>{@code IllegalStateException} — invalid state transition → 409</li>
     *   <li>{@code IllegalArgumentException} — validation / not-found → 400/404</li>
     *   <li>Aggregate not found (Axon) → 404</li>
     *   <li>Anything else → 500</li>
     * </ul>
     */
    private Response dispatchCommand(Object command) {
        try {
            commandGateway.sendAndWait(command);
            return Response.ok().build();
        } catch (CommandExecutionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            return mapExceptionToResponse(cause);
        } catch (Exception ex) {
            return mapExceptionToResponse(ex);
        }
    }

    private Response mapExceptionToResponse(Throwable ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();

        if (ex instanceof IllegalStateException
                || ex instanceof AggregateStreamCreationException
                || ex instanceof ConcurrencyException) {
            // AggregateStreamCreationException — duplicate aggregate ID (sequence 0 PK conflict)
            // ConcurrencyException            — concurrent write conflict on existing aggregate
            // IllegalStateException           — invalid state transition (e.g., completing a DONE task)
            log.debug("Command rejected — conflict: {}", message);
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ApiError("CONFLICT", message)).build();
        }

        if (ex instanceof IllegalArgumentException) {
            log.debug("Command rejected — bad argument: {}", message);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError("BAD_REQUEST", message)).build();
        }

        // Axon throws AggregateNotFoundException when the aggregate ID is unknown.
        String exName = ex.getClass().getSimpleName();
        if (exName.contains("NotFound") || exName.contains("AggregateNotFound")) {
            log.debug("Command rejected — not found: {}", message);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ApiError("NOT_FOUND", message)).build();
        }

        log.error("Unexpected error processing command", ex);
        return Response.serverError()
                .entity(new ApiError("INTERNAL_ERROR", "An unexpected server error occurred."))
                .build();
    }

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
}
