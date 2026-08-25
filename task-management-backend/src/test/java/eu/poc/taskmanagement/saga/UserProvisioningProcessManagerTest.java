package eu.poc.taskmanagement.saga;

import eu.poc.taskmanagement.integration.userdirectory.HttpExternalUserDirectoryClient;
import eu.poc.taskmanagement.model.TaskType;
import eu.poc.taskmanagement.projection.audittrail.AuditTrailEntry;
import eu.poc.taskmanagement.test.CommandDispatchHarness;
import eu.poc.taskmanagement.test.QueryStore;
import eu.poc.taskmanagement.testdata.AssignTaskCommandTestDataBuilder;
import eu.poc.taskmanagement.testdata.CreateTaskCommandTestDataBuilder;
import eu.poc.taskmanagement.testdata.StartTaskCommandTestDataBuilder;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link UserProvisioningProcessManager}, the Axon-5
 * replacement for the former {@code UserProvisioningCompletionSaga}.
 *
 * <p>The process manager now keeps its per-task state in a transactional
 * {@link ProvisioningState} database row (rather than in memory), so it can only
 * be exercised inside a running Quarkus container. This test therefore boots the
 * app with a {@link FakeSchedulerProfile} that swaps the real
 * {@code ExecutorDeadlineScheduler} for a deterministic {@link FakeDeadlineScheduler},
 * letting each poll be fired on demand. The external directory lookup is mocked.
 */
@QuarkusTest
@TestProfile(UserProvisioningProcessManagerTest.FakeSchedulerProfile.class)
class UserProvisioningProcessManagerTest {

    /** Enables the {@link FakeDeadlineScheduler} CDI alternative for this test only. */
    public static class FakeSchedulerProfile implements QuarkusTestProfile {
        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(FakeDeadlineScheduler.class);
        }
    }

    private static final Set<String> EXPECTED_USERS = Set.of("alice", "bob");

    @Inject
    CommandDispatchHarness commandDispatchHarness;

    @Inject
    QueryStore queryStore;

    @Inject
    FakeDeadlineScheduler scheduler;

    private HttpExternalUserDirectoryClient directoryMock;

    @BeforeEach
    void setup() {
        scheduler.reset();
        directoryMock = Mockito.mock(HttpExternalUserDirectoryClient.class);
        QuarkusMock.installMockForType(directoryMock, HttpExternalUserDirectoryClient.class);
    }

    private String startProvisioningTask() throws Exception {
        String taskId = "prov-" + System.nanoTime();
        var create = CreateTaskCommandTestDataBuilder.valid()
                .taskId(taskId)
                .title("Provision users")
                .description("Create users in external system")
                .groupName("iam-admins")
                .deadline(Instant.now().plusSeconds(3600))
                .taskType(TaskType.USER_PROVISIONING)
                .expectedExternalUsers(List.copyOf(EXPECTED_USERS))
                .build();
        var assign = AssignTaskCommandTestDataBuilder.valid().taskId(taskId).assigneeName("alice").build();
        var start = StartTaskCommandTestDataBuilder.valid().taskId(taskId).build();

        commandDispatchHarness.dispatch(create);
        commandDispatchHarness.dispatch(assign);
        commandDispatchHarness.dispatch(start);
        return taskId;
    }

    private long provisioningRowCount(String taskId) {
        return QuarkusTransaction.requiringNew()
                .call(() -> ProvisioningState.count("taskId = ?1", taskId));
    }

    @Test
    @DisplayName("Persists provisioning state on start and completes durably once all expected users exist")
    void completesTaskWhenAllExpectedUsersExist() throws Exception {
        Mockito.when(directoryMock.fetchCreatedUsers(Mockito.anyString())).thenReturn(EXPECTED_USERS);

        String taskId = startProvisioningTask();
        // State row exists while polling and three domain events are stored so far.
        assertEquals(1L, provisioningRowCount(taskId), "provisioning state row must exist while polling");
        assertEquals(3L, queryStore.countDomainEvents(taskId));

        // Fire the poll: all users present → task is completed.
        scheduler.fireNext();

        List<AuditTrailEntry> audit = queryStore.getAuditTrail(taskId);
        assertTrue(audit.stream().anyMatch(e -> "TaskCompletedEvent".equals(e.eventType)),
                "completion must be recorded");
        // Escalation runs on the scheduler thread; the completion event MUST be
        // durably persisted in the event store (created+assigned+started+completed = 4).
        assertEquals(4L, queryStore.countDomainEvents(taskId),
                "TaskCompletedEvent must be persisted in the event store, not only in the projection");
        // The process state row is cleaned up on completion.
        assertEquals(0L, provisioningRowCount(taskId), "provisioning state row must be removed after completion");
    }

    @Test
    @DisplayName("Keeps polling (and keeps its state) while expected users are still missing")
    void keepsPollingWhenUsersAreMissing() throws Exception {
        Mockito.when(directoryMock.fetchCreatedUsers(Mockito.anyString())).thenReturn(Set.of("alice"));

        String taskId = startProvisioningTask();

        scheduler.fireNext();

        // No completion; state row retained; another poll scheduled.
        assertEquals(3L, queryStore.countDomainEvents(taskId), "no completion event should be produced");
        assertEquals(1L, provisioningRowCount(taskId), "provisioning state row must be retained while polling");
        List<AuditTrailEntry> audit = queryStore.getAuditTrail(taskId);
        assertFalse(audit.stream().anyMatch(e -> "TaskCompletedEvent".equals(e.eventType)));
        assertTrue(scheduler.hasPending(), "a follow-up poll must be scheduled");
    }

    @Test
    @DisplayName("Ignores non-provisioning tasks (no state row created)")
    void ignoresNonProvisioningTasks() throws Exception {
        Mockito.when(directoryMock.fetchCreatedUsers(Mockito.anyString())).thenReturn(EXPECTED_USERS);

        String taskId = "std-" + System.nanoTime();
        var create = CreateTaskCommandTestDataBuilder.valid()
                .taskId(taskId)
                .title("Standard task")
                .description("Not a provisioning task")
                .groupName("ops-team")
                .deadline(Instant.now().plusSeconds(3600))
                .taskType(TaskType.STANDARD)
                .expectedExternalUsers(List.of())
                .build();
        commandDispatchHarness.dispatch(create);

        assertEquals(0L, provisioningRowCount(taskId),
                "no provisioning state must be created for a STANDARD task");
    }
}
