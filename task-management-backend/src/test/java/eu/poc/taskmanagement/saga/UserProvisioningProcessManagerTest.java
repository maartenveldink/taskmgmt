package eu.poc.taskmanagement.saga;

import eu.poc.taskmanagement.integration.userdirectory.ExternalUserDirectoryClient;
import eu.poc.taskmanagement.model.TaskStatus;
import eu.poc.taskmanagement.model.TaskType;
import eu.poc.taskmanagement.model.command.CompleteTaskCommand;
import eu.poc.taskmanagement.model.event.TaskCreatedEvent;
import eu.poc.taskmanagement.model.event.TaskStartedEvent;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;

/**
 * Unit tests for {@link UserProvisioningProcessManager}, the Axon-5 replacement
 * for the former {@code UserProvisioningCompletionSaga}.
 *
 * <p>Uses a deterministic {@link FakeDeadlineScheduler} to drive the poll loop
 * on demand, a mocked {@link ExternalUserDirectoryClient} for the directory
 * lookups, and a mocked {@link CommandGateway} to assert task completion.
 */
class UserProvisioningProcessManagerTest {

    private static final String TASK_ID = "provisioning-task-1";

    private FakeDeadlineScheduler scheduler;
    private ExternalUserDirectoryClient externalClient;
    private CommandGateway commandGateway;
    private UserProvisioningProcessManager processManager;

    @BeforeEach
    void setup() {
        scheduler = new FakeDeadlineScheduler();
        externalClient = Mockito.mock(ExternalUserDirectoryClient.class);
        commandGateway = Mockito.mock(CommandGateway.class);
        processManager = new UserProvisioningProcessManager(scheduler, externalClient, commandGateway, Runnable::run);
    }

    private TaskCreatedEvent provisioningCreated() {
        return new TaskCreatedEvent(
                TASK_ID,
                "Provision users",
                "Create users in external system",
                "iam-admins",
                null,
                Instant.now().plus(1, ChronoUnit.HOURS),
                TaskType.USER_PROVISIONING,
                List.of("alice", "bob"));
    }

    @Test
    @DisplayName("Completes the task once all expected users exist")
    void completesTaskWhenAllExpectedUsersExist() {
        Mockito.when(externalClient.fetchCreatedUsers(TASK_ID))
                .thenReturn(Set.of("alice", "bob"));

        processManager.on(provisioningCreated());
        processManager.on(new TaskStartedEvent(TASK_ID, TaskStatus.ASSIGNED));

        // First poll fires — all users present → CompleteTaskCommand dispatched.
        scheduler.fireNext();

        Mockito.verify(commandGateway).sendAndWait(argThat(command ->
                command instanceof CompleteTaskCommand completeTaskCommand
                        && TASK_ID.equals(completeTaskCommand.taskId())));
    }

    @Test
    @DisplayName("Reschedules another poll while expected users are still missing")
    void keepsPollingWhenUsersAreMissing() {
        Mockito.when(externalClient.fetchCreatedUsers(TASK_ID))
                .thenReturn(Set.of("alice"));

        processManager.on(provisioningCreated());
        processManager.on(new TaskStartedEvent(TASK_ID, TaskStatus.ASSIGNED));

        // First poll fires — not all users present → another poll is scheduled.
        scheduler.fireNext();

        assertTrue(scheduler.hasPending());
        Mockito.verify(commandGateway, Mockito.never()).sendAndWait(Mockito.any());
    }

    @Test
    @DisplayName("Ignores non-provisioning tasks")
    void ignoresNonProvisioningTasks() {
        TaskCreatedEvent standard = new TaskCreatedEvent(
                TASK_ID, "Standard", "desc", "grp", null,
                Instant.now().plus(1, ChronoUnit.HOURS), TaskType.STANDARD, List.of());

        processManager.on(standard);
        processManager.on(new TaskStartedEvent(TASK_ID, TaskStatus.ASSIGNED));

        // No polling scheduled for a non-provisioning task.
        org.junit.jupiter.api.Assertions.assertFalse(scheduler.hasPending());
        Mockito.verifyNoInteractions(externalClient, commandGateway);
    }
}
