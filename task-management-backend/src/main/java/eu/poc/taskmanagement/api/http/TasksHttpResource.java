package eu.poc.taskmanagement.api.http;

import eu.poc.taskmanagement.api.error.ApiError;
import eu.poc.taskmanagement.api.mapping.TasksHttpMapper;
import eu.poc.taskmanagement.application.command.TaskCommandApplicationService;
import eu.poc.taskmanagement.application.query.TaskQueryApplicationService;
import eu.poc.taskmanagement.generated.api.TasksApi;
import eu.poc.taskmanagement.generated.model.AssignTaskRequest;
import eu.poc.taskmanagement.generated.model.AuditTrailEntry;
import eu.poc.taskmanagement.generated.model.CancelTaskRequest;
import eu.poc.taskmanagement.generated.model.CreateTaskRequest;
import eu.poc.taskmanagement.generated.model.ReassignTaskRequest;
import eu.poc.taskmanagement.generated.model.RejectTaskRequest;
import eu.poc.taskmanagement.generated.model.TaskStatus;
import eu.poc.taskmanagement.generated.model.TaskView;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.axonframework.modelling.entity.EntityAlreadyExistsForCreationalCommandHandlerException;
import org.axonframework.modelling.entity.EntityMissingForInstanceCommandHandlerException;
import org.axonframework.modelling.ConcurrencyException;

import java.time.OffsetDateTime;
import java.util.List;

@ApplicationScoped
@Path("/tasks")
public class TasksHttpResource implements TasksApi {

    @Inject
    TaskCommandApplicationService commandService;

    @Inject
    TaskQueryApplicationService queryService;

    @Inject
    TasksHttpMapper mapper;

    @Inject
    Validator validator;

    @ConfigProperty(name = "task.default-group", defaultValue = "unassigned")
    String defaultGroup;

    @Override
    @Transactional
    public void createTask(CreateTaskRequest createTaskRequest) {
        validate(mapper.toInternal(createTaskRequest));
        executeCommand(() -> commandService.handle(mapper.toCreateTaskCommand(createTaskRequest, defaultGroup)));
    }

    @Override
    @Transactional
    public void assignTask(String id, AssignTaskRequest assignTaskRequest) {
        validate(mapper.toInternal(assignTaskRequest));
        executeCommand(() -> commandService.handle(mapper.toAssignTaskCommand(id, assignTaskRequest)));
    }

    @Override
    @Transactional
    public void reassignTask(String id, ReassignTaskRequest reassignTaskRequest) {
        validate(mapper.toInternal(reassignTaskRequest));
        executeCommand(() -> commandService.handle(mapper.toReassignTaskCommand(id, reassignTaskRequest)));
    }

    @Override
    @Transactional
    public void startTask(String id) {
        executeCommand(() -> commandService.handle(mapper.toStartTaskCommand(id)));
    }

    @Override
    @Transactional
    public void completeTask(String id) {
        executeCommand(() -> commandService.handle(mapper.toCompleteTaskCommand(id)));
    }

    @Override
    @Transactional
    public void cancelTask(String id, CancelTaskRequest cancelTaskRequest) {
        validate(mapper.toInternal(cancelTaskRequest));
        executeCommand(() -> commandService.handle(mapper.toCancelTaskCommand(id, cancelTaskRequest)));
    }

    @Override
    @Transactional
    public void rejectTask(String id, RejectTaskRequest rejectTaskRequest) {
        validate(mapper.toInternal(rejectTaskRequest));
        executeCommand(() -> commandService.handle(mapper.toRejectTaskCommand(id, rejectTaskRequest)));
    }

    @Override
    public List<TaskView> getAllTasks(TaskStatus status,
                                      OffsetDateTime deadlineBefore,
                                      OffsetDateTime deadlineAfter,
                                      Integer offset,
                                      Integer limit) {
        List<eu.poc.taskmanagement.projection.tasks.TaskView> tasks = queryService.getAllTasks(
                mapper.toInternal(status),
                mapper.toInstant(deadlineBefore),
                mapper.toInstant(deadlineAfter),
                sanitizeOffset(offset),
                sanitizeLimit(limit));

        return mapper.toGeneratedTaskViews(tasks);
    }

    @Override
    public List<TaskView> getTasksByUser(String userName,
                                         TaskStatus status,
                                         OffsetDateTime deadlineBefore,
                                         OffsetDateTime deadlineAfter,
                                         Integer offset,
                                         Integer limit) {
        List<eu.poc.taskmanagement.projection.tasks.TaskView> tasks = queryService.getTasksByUser(
                userName,
                mapper.toInternal(status),
                mapper.toInstant(deadlineBefore),
                mapper.toInstant(deadlineAfter),
                sanitizeOffset(offset),
                sanitizeLimit(limit));

        return mapper.toGeneratedTaskViews(tasks);
    }

    @Override
    public List<TaskView> getTasksByGroup(String groupName,
                                          TaskStatus status,
                                          OffsetDateTime deadlineBefore,
                                          OffsetDateTime deadlineAfter,
                                          Integer offset,
                                          Integer limit) {
        List<eu.poc.taskmanagement.projection.tasks.TaskView> tasks = queryService.getTasksByGroup(
                groupName,
                mapper.toInternal(status),
                mapper.toInstant(deadlineBefore),
                mapper.toInstant(deadlineAfter),
                sanitizeOffset(offset),
                sanitizeLimit(limit));

        return mapper.toGeneratedTaskViews(tasks);
    }

    @Override
    public List<AuditTrailEntry> getAuditTrail(String id) {
        List<eu.poc.taskmanagement.projection.audittrail.AuditTrailEntry> entries = queryService.getAuditTrail(id);
        return mapper.toGeneratedAuditEntries(entries);
    }

    private void executeCommand(Runnable commandOperation) {
        try {
            commandOperation.run();
        } catch (RuntimeException ex) {
            throw new WebApplicationException(mapExceptionToResponse(ex));
        }
    }

    private Response mapExceptionToResponse(Throwable ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        // An instance command targeting a task that has never been created — the
        // Axon 5 equivalent of the Axon 4 AggregateNotFoundException.
        if (ex instanceof EntityMissingForInstanceCommandHandlerException) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ApiError("NOT_FOUND", message))
                    .build();
        }
        if (ex instanceof IllegalStateException
                || ex instanceof EntityAlreadyExistsForCreationalCommandHandlerException
                || ex instanceof ConcurrencyException) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ApiError("CONFLICT", message))
                    .build();
        }
        if (ex instanceof IllegalArgumentException) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiError("BAD_REQUEST", message))
                    .build();
        }
        String exName = ex.getClass().getSimpleName();
        if (exName.contains("NotFound") || exName.contains("AggregateNotFound")) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ApiError("NOT_FOUND", message))
                    .build();
        }
        return Response.serverError()
                .entity(new ApiError("INTERNAL_ERROR", "An unexpected server error occurred."))
                .build();
    }

    private int sanitizeOffset(Integer offset) {
        int effectiveOffset = offset == null ? 0 : offset;
        if (effectiveOffset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        return effectiveOffset;
    }

    private int sanitizeLimit(Integer limit) {
        int effectiveLimit = limit == null ? 50 : limit;
        if (effectiveLimit <= 0 || effectiveLimit > 200) {
            throw new IllegalArgumentException("limit must be between 1 and 200");
        }
        return effectiveLimit;
    }

    private void validate(Object request) {
        if (request == null) {
            return;
        }
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
