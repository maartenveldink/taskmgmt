package eu.poc.taskmanagement.application.query;

import eu.poc.taskmanagement.model.TaskStatus;
import eu.poc.taskmanagement.projection.audittrail.AuditTrailEntry;
import eu.poc.taskmanagement.projection.audittrail.query.GetAuditTrailByTaskQuery;
import eu.poc.taskmanagement.projection.tasks.TaskView;
import eu.poc.taskmanagement.projection.tasks.query.GetAllTasksQuery;
import eu.poc.taskmanagement.projection.tasks.query.GetTasksByGroupQuery;
import eu.poc.taskmanagement.projection.tasks.query.GetTasksByUserQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionException;

@ApplicationScoped
public class TaskQueryApplicationService {

    @Inject
    QueryGateway queryGateway;

    public List<TaskView> getAllTasks(TaskStatus status,
                               Instant deadlineBefore,
                               Instant deadlineAfter,
                               int offset,
                               int limit) {
        return query(new GetAllTasksQuery(status, deadlineBefore, deadlineAfter, offset, limit),
                TaskView.class);
    }

    public List<TaskView> getTasksByUser(String userName,
                                  TaskStatus status,
                                  Instant deadlineBefore,
                                  Instant deadlineAfter,
                                  int offset,
                                  int limit) {
        return query(new GetTasksByUserQuery(
                userName, status, deadlineBefore, deadlineAfter, offset, limit), TaskView.class);
    }

    public List<TaskView> getTasksByGroup(String groupName,
                                    TaskStatus status,
                                    Instant deadlineBefore,
                                    Instant deadlineAfter,
                                    int offset,
                                    int limit) {
        return query(new GetTasksByGroupQuery(
                groupName, status, deadlineBefore, deadlineAfter, offset, limit), TaskView.class);
    }

    public List<AuditTrailEntry> getAuditTrail(String taskId) {
        return query(new GetAuditTrailByTaskQuery(taskId), AuditTrailEntry.class);
    }

    private <R> List<R> query(Object queryMessage, Class<R> responseType) {
        try {
            return queryGateway.queryMany(queryMessage, responseType).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("Query execution failed", cause);
        }
    }
}
