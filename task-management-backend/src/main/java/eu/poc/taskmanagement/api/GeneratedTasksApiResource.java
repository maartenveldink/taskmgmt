package eu.poc.taskmanagement.api;

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
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.OffsetDateTime;
import java.util.List;

@ApplicationScoped
@Path("/tasks")
public class GeneratedTasksApiResource implements TasksApi {

    @Inject
    TaskCommandDispatcher commandDispatcher;

    @Inject
    TaskQueryService queryService;

    @Inject
    TaskApiMapper mapper;

    @ConfigProperty(name = "task.default-group", defaultValue = "unassigned")
    String defaultGroup;

    @Override
    @Transactional
    public void createTask(CreateTaskRequest createTaskRequest) {
        Response response = commandDispatcher.createTask(mapper.toInternal(createTaskRequest), defaultGroup);
        ensureCommandSucceeded(response);
    }

    @Override
    @Transactional
    public void assignTask(String id, AssignTaskRequest assignTaskRequest) {
        Response response = commandDispatcher.assignTask(id, mapper.toInternal(assignTaskRequest));
        ensureCommandSucceeded(response);
    }

    @Override
    @Transactional
    public void reassignTask(String id, ReassignTaskRequest reassignTaskRequest) {
        Response response = commandDispatcher.reassignTask(id, mapper.toInternal(reassignTaskRequest));
        ensureCommandSucceeded(response);
    }

    @Override
    @Transactional
    public void startTask(String id) {
        Response response = commandDispatcher.startTask(id);
        ensureCommandSucceeded(response);
    }

    @Override
    @Transactional
    public void completeTask(String id) {
        Response response = commandDispatcher.completeTask(id);
        ensureCommandSucceeded(response);
    }

    @Override
    @Transactional
    public void cancelTask(String id, CancelTaskRequest cancelTaskRequest) {
        Response response = commandDispatcher.cancelTask(id, mapper.toInternal(cancelTaskRequest));
        ensureCommandSucceeded(response);
    }

    @Override
    @Transactional
    public void rejectTask(String id, RejectTaskRequest rejectTaskRequest) {
        Response response = commandDispatcher.rejectTask(id, mapper.toInternal(rejectTaskRequest));
        ensureCommandSucceeded(response);
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

    private void ensureCommandSucceeded(Response response) {
        if (response.getStatus() >= 400) {
            throw new WebApplicationException(response);
        }
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
}
