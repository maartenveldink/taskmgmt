package eu.poc.taskmanagement.api;

import eu.poc.taskmanagement.model.TaskStatus;
import eu.poc.taskmanagement.projection.audittrail.AuditTrailEntry;
import eu.poc.taskmanagement.projection.tasks.TaskView;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static eu.poc.taskmanagement.api.CreateTaskCommandTestDataBuilder.aCreateTaskCommand;
import static eu.poc.taskmanagement.api.AssignTaskCommandTestDataBuilder.anAssignTaskCommand;
import static eu.poc.taskmanagement.api.StartTaskCommandTestDataBuilder.aStartTaskCommand;

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
        String taskId = "flow-" + System.nanoTime();

        // Build and dispatch commands using fluent builders
        commandDispatchHarness.dispatch(
                aCreateTaskCommand()
                        .withTaskId(taskId)
                        .withTitle("Flow task")
                        .withDescription("Unified setup test")
                        .withGroupName("ops-team")
                        .withDeadlineInSeconds(1)
                        .build());

        commandDispatchHarness.dispatch(
                anAssignTaskCommand()
                        .withTaskId(taskId)
                        .assignToUser("alice")
                        .build());

        commandDispatchHarness.dispatch(
                aStartTaskCommand()
                        .withTaskId(taskId)
                        .build());

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
