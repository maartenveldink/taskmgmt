package eu.poc.taskmanagement.api;

import eu.poc.taskmanagement.api.dto.AssignTaskRequest;
import eu.poc.taskmanagement.api.dto.CancelTaskRequest;
import eu.poc.taskmanagement.api.dto.CreateTaskRequest;
import eu.poc.taskmanagement.api.dto.ReassignTaskRequest;
import eu.poc.taskmanagement.api.dto.RejectTaskRequest;
import eu.poc.taskmanagement.api.error.ApiError;
import eu.poc.taskmanagement.model.command.AssignTaskCommand;
import eu.poc.taskmanagement.model.command.CancelTaskCommand;
import eu.poc.taskmanagement.model.command.CompleteTaskCommand;
import eu.poc.taskmanagement.model.command.CreateTaskCommand;
import eu.poc.taskmanagement.model.command.ReassignTaskCommand;
import eu.poc.taskmanagement.model.command.RejectTaskCommand;
import eu.poc.taskmanagement.model.command.StartTaskCommand;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.command.AggregateStreamCreationException;
import org.axonframework.modelling.command.ConcurrencyException;

@Slf4j
@ApplicationScoped
class TaskCommandDispatcher {

    @Inject
    CommandGateway commandGateway;

    Response createTask(CreateTaskRequest req, String defaultGroup) {
        String group = (req.groupName() != null && !req.groupName().isBlank())
                ? req.groupName() : defaultGroup;

        return dispatch(new CreateTaskCommand(
                req.correlationId(),
                req.title(),
                req.description(),
                group,
                req.deadline()
        ));
    }

    Response assignTask(String id, AssignTaskRequest req) {
        return dispatch(new AssignTaskCommand(id, req.assigneeName(), req.assigneeType()));
    }

    Response reassignTask(String id, ReassignTaskRequest req) {
        return dispatch(new ReassignTaskCommand(id, req.newAssigneeName(), req.newAssigneeType()));
    }

    Response startTask(String id) {
        return dispatch(new StartTaskCommand(id));
    }

    Response completeTask(String id) {
        return dispatch(new CompleteTaskCommand(id));
    }

    Response cancelTask(String id, CancelTaskRequest req) {
        String reason = req != null ? req.reason() : null;
        return dispatch(new CancelTaskCommand(id, reason));
    }

    Response rejectTask(String id, RejectTaskRequest req) {
        String reason = req != null ? req.reason() : null;
        return dispatch(new RejectTaskCommand(id, reason));
    }

    Response dispatch(Object command) {
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
            log.debug("Command rejected — conflict: {}", message);
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ApiError("CONFLICT", message)).build();
        }

        if (ex instanceof IllegalArgumentException) {
            log.debug("Command rejected — bad argument: {}", message);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError("BAD_REQUEST", message)).build();
        }

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
}
