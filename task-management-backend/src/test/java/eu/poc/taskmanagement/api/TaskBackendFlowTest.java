package eu.poc.taskmanagement.api;

import eu.poc.taskmanagement.model.TaskStatus;
import eu.poc.taskmanagement.projection.audittrail.AuditTrailEntry;
import eu.poc.taskmanagement.projection.tasks.TaskView;
import eu.poc.taskmanagement.test.CommandDispatchHarness;
import eu.poc.taskmanagement.test.QueryStore;
import eu.poc.taskmanagement.testdata.AssignTaskCommandTestDataBuilder;
import eu.poc.taskmanagement.testdata.CreateTaskCommandTestDataBuilder;
import eu.poc.taskmanagement.testdata.StartTaskCommandTestDataBuilder;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

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
        String taskId = "flow-" + System.nanoTime();

        // Build and dispatch commands using test data builders
        var createCommand = CreateTaskCommandTestDataBuilder.valid()
                .taskId(taskId)
                .title("Flow task")
                .description("Unified setup test")
                .groupName("ops-team")
                .deadline(Instant.now().plusSeconds(1))
                .build();

        var assignCommand = AssignTaskCommandTestDataBuilder.valid()
                .taskId(taskId)
                .assigneeName("alice")
                .build();

        var startCommand = StartTaskCommandTestDataBuilder.valid()
                .taskId(taskId)
                .build();

        commandDispatchHarness.dispatch(createCommand);
        commandDispatchHarness.dispatch(assignCommand);
        commandDispatchHarness.dispatch(startCommand);

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
