package eu.poc.taskmanagement.api;

import eu.poc.taskmanagement.model.TaskStatus;
import eu.poc.taskmanagement.model.command.AssignTaskCommand;
import eu.poc.taskmanagement.model.command.AssigneeType;
import eu.poc.taskmanagement.model.command.CreateTaskCommand;
import eu.poc.taskmanagement.model.command.StartTaskCommand;
import eu.poc.taskmanagement.projection.audittrail.AuditTrailEntry;
import eu.poc.taskmanagement.projection.tasks.TaskView;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
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
    QueryStore queryStore;

    @Test
    void commandSagaAndQueryStoresWorkInSingleSetup() throws Exception {
        String taskId = "flow-" + UUID.randomUUID();
        Instant deadline = Instant.now().plusSeconds(1);

        commandDispatchHarness.dispatch(new CreateTaskCommand(taskId, "Flow task", "Unified setup test", "ops-team", deadline));
        commandDispatchHarness.dispatch(new AssignTaskCommand(taskId, "alice", AssigneeType.USER));
        commandDispatchHarness.dispatch(new StartTaskCommand(taskId));

        // Verify task is in-progress via read model
        List<TaskView> inProgressTasks = queryStore.findTasksByStatus(TaskStatus.IN_PROGRESS);
        assertTrue(inProgressTasks.stream().anyMatch(task -> taskId.equals(task.taskId)));

        // Verify 3 events were published
        long domainEventCount = queryStore.countDomainEvents(taskId);
        assertEquals(3L, domainEventCount);

        // Wait for deadline to exceed and saga to escalate
        queryStore.waitForEvent(taskId, "TaskDeadlineExceededEvent", Duration.ofSeconds(6));

        // Verify audit trail contains escalation event
        List<AuditTrailEntry> audit = queryStore.getAuditTrail(taskId);
        assertTrue(audit.stream().anyMatch(entry -> "TaskDeadlineExceededEvent".equals(entry.eventType)));

        // Verify saga has cleaned up its associations
        long activeSagaAssociations = queryStore.countActiveSagaAssociations(taskId);
        assertEquals(0L, activeSagaAssociations);
    }
}
