package eu.poc.taskmanagement.api;

import eu.poc.taskmanagement.integration.userdirectory.HttpExternalUserDirectoryClient;
import eu.poc.taskmanagement.model.TaskType;
import eu.poc.taskmanagement.projection.audittrail.AuditTrailEntry;
import eu.poc.taskmanagement.test.CommandDispatchHarness;
import eu.poc.taskmanagement.test.QueryStore;
import eu.poc.taskmanagement.testdata.AssignTaskCommandTestDataBuilder;
import eu.poc.taskmanagement.testdata.CreateTaskCommandTestDataBuilder;
import eu.poc.taskmanagement.testdata.StartTaskCommandTestDataBuilder;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end durable-store guard for the user-provisioning completion flow.
 *
 * <p>The provisioning poll runs on a {@code ScheduledExecutorService} thread with
 * no ambient {@code @Transactional}.  When all expected users are present it
 * dispatches a {@code CompleteTaskCommand}, whose {@code TaskCompletedEvent} MUST
 * be durably written to the event store — not merely published in-memory to the
 * (separately {@code @Transactional}) projections.  This mirrors the deadline
 * regression guard in {@link TaskBackendFlowTest}.
 */
@QuarkusTest
class UserProvisioningFlowTest {

    private static final Set<String> EXPECTED_USERS = Set.of("ext-user-1", "ext-user-2");

    @Inject
    CommandDispatchHarness commandDispatchHarness;

    @Inject
    QueryStore queryStore;

    @BeforeEach
    void installDirectoryMock() {
        HttpExternalUserDirectoryClient mock = Mockito.mock(HttpExternalUserDirectoryClient.class);
        // All expected users already exist, so the very first poll completes the task.
        Mockito.when(mock.fetchCreatedUsers(Mockito.anyString())).thenReturn(EXPECTED_USERS);
        QuarkusMock.installMockForType(mock, HttpExternalUserDirectoryClient.class);
    }

    @Test
    void provisioningCompletionIsDurablyPersisted() throws Exception {
        String taskId = "provisioning-" + System.nanoTime();

        var createCommand = CreateTaskCommandTestDataBuilder.valid()
                .taskId(taskId)
                .title("Provisioning task")
                .description("Durable-store guard for provisioning completion")
                .groupName("ops-team")
                // Far-future deadline so the deadline process manager never fires here.
                .deadline(Instant.now().plusSeconds(3600))
                .taskType(TaskType.USER_PROVISIONING)
                .expectedExternalUsers(List.copyOf(EXPECTED_USERS))
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

        // Wait for the scheduler-thread poll to detect all users and complete the task.
        queryStore.waitForEvent(taskId, "TaskCompletedEvent", Duration.ofSeconds(15));

        List<AuditTrailEntry> audit = queryStore.getAuditTrail(taskId);
        assertTrue(audit.stream().anyMatch(entry -> "TaskCompletedEvent".equals(entry.eventType)),
                "Audit trail must contain the provisioning completion event");

        // Regression guard: completion is dispatched from a scheduler thread with no
        // ambient @Transactional. The event MUST be durably persisted in the event
        // store (created + assigned + started + completed = 4), not merely published
        // in-memory to the projection. A no-op transaction manager would leave this at 3.
        assertEquals(4L, queryStore.countDomainEvents(taskId),
                "TaskCompletedEvent must be persisted in the event store, not only in the projection");
    }
}
