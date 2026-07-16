package eu.poc.taskmanagement.saga;

import eu.poc.taskmanagement.integration.userdirectory.ExternalUserDirectoryClient;
import eu.poc.taskmanagement.model.TaskStatus;
import eu.poc.taskmanagement.model.TaskType;
import eu.poc.taskmanagement.model.command.CompleteTaskCommand;
import eu.poc.taskmanagement.model.event.TaskCreatedEvent;
import eu.poc.taskmanagement.model.event.TaskStartedEvent;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.test.saga.SagaTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.argThat;

class UserProvisioningCompletionSagaTest {

    private SagaTestFixture<UserProvisioningCompletionSaga> fixture;
    private ExternalUserDirectoryClient externalClient;
    private CommandGateway commandGateway;

    private static final String TASK_ID = "provisioning-task-1";

    @BeforeEach
    void setup() {
        fixture = new SagaTestFixture<>(UserProvisioningCompletionSaga.class);
        externalClient = Mockito.mock(ExternalUserDirectoryClient.class);
        commandGateway = Mockito.mock(CommandGateway.class);
        fixture.registerResource(externalClient);
        fixture.registerResource(commandGateway);
    }

    @Test
    void completesTaskWhenAllExpectedUsersExist() {
        Mockito.when(externalClient.fetchCreatedUsers(TASK_ID))
                .thenReturn(Set.of("alice", "bob"));

        TaskCreatedEvent created = new TaskCreatedEvent(
                TASK_ID,
                "Provision users",
                "Create users in external system",
                "iam-admins",
                null,
                fixture.currentTime().plus(Duration.ofHours(1)),
                TaskType.USER_PROVISIONING,
                List.of("alice", "bob")
        );

        fixture.givenNoPriorActivity()
                .whenPublishingA(created)
                .expectActiveSagas(1);

        fixture.givenAPublished(created)
                .whenPublishingA(new TaskStartedEvent(TASK_ID, TaskStatus.ASSIGNED))
                .expectActiveSagas(1);

        fixture.givenAPublished(created)
                .andThenAPublished(new TaskStartedEvent(TASK_ID, TaskStatus.ASSIGNED))
                .whenTimeElapses(Duration.ofSeconds(6))
                .expectActiveSagas(0);

        Mockito.verify(commandGateway).sendAndWait(argThat(command ->
                command instanceof CompleteTaskCommand completeTaskCommand
                        && TASK_ID.equals(completeTaskCommand.taskId())));
    }

    @Test
    void keepsSagaActiveWhenUsersAreMissing() {
        Mockito.when(externalClient.fetchCreatedUsers(TASK_ID))
                .thenReturn(Set.of("alice"));

        TaskCreatedEvent created = new TaskCreatedEvent(
                TASK_ID,
                "Provision users",
                "Create users in external system",
                "iam-admins",
                null,
                fixture.currentTime().plus(Duration.ofHours(1)),
                TaskType.USER_PROVISIONING,
                List.of("alice", "bob")
        );

        fixture.givenAPublished(created)
                .andThenAPublished(new TaskStartedEvent(TASK_ID, TaskStatus.ASSIGNED))
                .whenTimeElapses(Duration.ofSeconds(6))
                .expectActiveSagas(1);
    }
}
