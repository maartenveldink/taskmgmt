package eu.poc.taskmanagement.api;

import eu.poc.taskmanagement.model.TaskStatus;
import eu.poc.taskmanagement.model.command.AssignTaskCommand;
import eu.poc.taskmanagement.model.command.AssigneeType;
import eu.poc.taskmanagement.model.command.CreateTaskCommand;
import eu.poc.taskmanagement.model.command.StartTaskCommand;
import eu.poc.taskmanagement.projection.audittrail.AuditTrailEntry;
import eu.poc.taskmanagement.projection.audittrail.query.GetAuditTrailByTaskQuery;
import eu.poc.taskmanagement.projection.tasks.TaskView;
import eu.poc.taskmanagement.projection.tasks.query.GetAllTasksQuery;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end integration test in one Quarkus setup:
 * command handling + saga + query/read stores.
 */
@QuarkusTest
class TaskBackendFlowTest {

    @Inject
    CommandDispatchHarness commandDispatchHarness;

    @Inject
    QueryGateway queryGateway;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    void commandSagaAndQueryStoresWorkInSingleSetup() throws Exception {
        String taskId = "flow-" + UUID.randomUUID();
        Instant deadline = Instant.now().plusSeconds(1);

        commandDispatchHarness.dispatch(new CreateTaskCommand(taskId, "Flow task", "Unified setup test", "ops-team", deadline));
        commandDispatchHarness.dispatch(new AssignTaskCommand(taskId, "alice", AssigneeType.USER));
        commandDispatchHarness.dispatch(new StartTaskCommand(taskId));

        List<TaskView> inProgressTasks = query(
                new GetAllTasksQuery(TaskStatus.IN_PROGRESS, null, null, 0, 50), TaskView.class);
        assertTrue(inProgressTasks.stream().anyMatch(task -> taskId.equals(task.taskId)));

        long domainEventCount = count(
                "select count(*) from domainevententry where aggregateidentifier = ?1", taskId);
        assertEquals(3L, domainEventCount);

        awaitDeadlineExceeded(taskId, Duration.ofSeconds(6));

        List<AuditTrailEntry> audit = query(new GetAuditTrailByTaskQuery(taskId), AuditTrailEntry.class);
        assertTrue(audit.stream().anyMatch(entry -> "TaskDeadlineExceededEvent".equals(entry.eventType)));

        long activeSagaAssociations = count(
                "select count(*) from associationvalueentry where associationvalue = ?1", taskId);
        assertEquals(0L, activeSagaAssociations);
    }

    private <T> List<T> query(Object queryMessage, Class<T> responseType) throws Exception {
        return queryGateway.query(queryMessage, ResponseTypes.multipleInstancesOf(responseType)).get();
    }

    private long count(String sql, String parameter) {
        Number result = (Number) entityManager.createNativeQuery(sql)
                .setParameter(1, parameter)
                .getSingleResult();
        return result.longValue();
    }

    private void awaitDeadlineExceeded(String taskId, Duration timeout) throws Exception {
        long timeoutAt = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < timeoutAt) {
            List<AuditTrailEntry> audit = query(new GetAuditTrailByTaskQuery(taskId), AuditTrailEntry.class);
            if (audit.stream().anyMatch(entry -> "TaskDeadlineExceededEvent".equals(entry.eventType))) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Deadline escalation event not observed within timeout for taskId=" + taskId);
    }
}
