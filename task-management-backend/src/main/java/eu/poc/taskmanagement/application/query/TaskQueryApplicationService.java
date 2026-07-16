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
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;

@ApplicationScoped
public class TaskQueryApplicationService {

    @Inject
    QueryGateway queryGateway;

    public List<TaskView> getAllTasks(TaskStatus status,
                               Instant deadlineBefore,
                               Instant deadlineAfter,
                               int offset,
                               int limit) {
        return query(new GetAllTasksQuery(status, deadlineBefore, deadlineAfter, offset, limit));
    }

    public List<TaskView> getTasksByUser(String userName,
                                  TaskStatus status,
                                  Instant deadlineBefore,
                                  Instant deadlineAfter,
                                  int offset,
                                  int limit) {
        return query(new GetTasksByUserQuery(
                userName, status, deadlineBefore, deadlineAfter, offset, limit));
    }

    public List<TaskView> getTasksByGroup(String groupName,
                                    TaskStatus status,
                                    Instant deadlineBefore,
                                    Instant deadlineAfter,
                                    int offset,
                                    int limit) {
        return query(new GetTasksByGroupQuery(
                groupName, status, deadlineBefore, deadlineAfter, offset, limit));
    }

    public List<AuditTrailEntry> getAuditTrail(String taskId) {
        return query(new GetAuditTrailByTaskQuery(taskId));
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
