package eu.poc.taskmanagement.saga;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import eu.poc.taskmanagement.integration.userdirectory.ExternalUserDirectoryClient;
import eu.poc.taskmanagement.model.TaskStatus;
import eu.poc.taskmanagement.model.TaskType;
import eu.poc.taskmanagement.model.command.CompleteTaskCommand;
import eu.poc.taskmanagement.model.event.TaskAssignedEvent;
import eu.poc.taskmanagement.model.event.TaskCancelledEvent;
import eu.poc.taskmanagement.model.event.TaskCompletedEvent;
import eu.poc.taskmanagement.model.event.TaskCreatedEvent;
import eu.poc.taskmanagement.model.event.TaskRejectedEvent;
import eu.poc.taskmanagement.model.event.TaskStartedEvent;
import jakarta.inject.Inject;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaLifecycle;
import org.axonframework.modelling.saga.StartSaga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class UserProvisioningCompletionSaga {

    private static final Logger LOG = LoggerFactory.getLogger(UserProvisioningCompletionSaga.class);
    static final String POLL_DEADLINE = "user-provisioning-poll";
    private static final long POLL_INTERVAL_SECONDS = 5L;

    @Inject
    transient DeadlineManager deadlineManager;

    @Inject
    transient ExternalUserDirectoryClient externalUserDirectoryClient;

    @Inject
    transient CommandGateway commandGateway;

    private String taskId;
    private Instant deadline;
    private String scheduleId;
    private TaskStatus lastKnownStatus;
    private Set<String> expectedUsers;

    @StartSaga
    @SagaEventHandler(associationProperty = "taskId")
    public void on(TaskCreatedEvent event) {
        if (event.taskType() != TaskType.USER_PROVISIONING) {
            SagaLifecycle.end();
            return;
        }

        if (event.expectedExternalUsers() == null || event.expectedExternalUsers().isEmpty()) {
            LOG.warn("Not starting user provisioning saga for taskId={} because expectedExternalUsers is empty", event.taskId());
            SagaLifecycle.end();
            return;
        }

        this.taskId = event.taskId();
        this.deadline = event.deadline();
        this.lastKnownStatus = TaskStatus.CREATED;
        this.expectedUsers = new HashSet<>(event.expectedExternalUsers());
    }

    @SagaEventHandler(associationProperty = "taskId")
    public void on(TaskAssignedEvent event) {
        this.lastKnownStatus = TaskStatus.ASSIGNED;
    }

    @SagaEventHandler(associationProperty = "taskId")
    public void on(TaskStartedEvent event) {
        this.lastKnownStatus = TaskStatus.IN_PROGRESS;
        scheduleNextPoll();
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "taskId")
    public void on(TaskCompletedEvent event) {
        this.lastKnownStatus = TaskStatus.DONE;
        cancelScheduledPoll();
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "taskId")
    public void on(TaskCancelledEvent event) {
        this.lastKnownStatus = TaskStatus.CANCELLED;
        cancelScheduledPoll();
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "taskId")
    public void on(TaskRejectedEvent event) {
        this.lastKnownStatus = TaskStatus.REJECTED;
        cancelScheduledPoll();
    }

    @DeadlineHandler(deadlineName = POLL_DEADLINE)
    public void onPollDeadline(UserProvisioningPollPayload payload) {
        if (lastKnownStatus == null || lastKnownStatus.isTerminal()) {
            SagaLifecycle.end();
            return;
        }

        if (Instant.now().isAfter(deadline)) {
            LOG.warn("User provisioning completion saga timed out for taskId={}, expectedUsers={}", taskId, expectedUsers);
            SagaLifecycle.end();
            return;
        }

        if (lastKnownStatus != TaskStatus.IN_PROGRESS) {
            scheduleNextPoll();
            return;
        }

        Set<String> createdUsers = externalUserDirectoryClient.fetchCreatedUsers(taskId);
        if (createdUsers.containsAll(expectedUsers)) {
            commandGateway.sendAndWait(new CompleteTaskCommand(taskId));
            SagaLifecycle.end();
            return;
        }

        scheduleNextPoll();
    }

    private void scheduleNextPoll() {
        this.scheduleId = deadlineManager.schedule(
                Instant.now().plusSeconds(POLL_INTERVAL_SECONDS),
                POLL_DEADLINE,
                new UserProvisioningPollPayload(taskId, deadline)
        );
    }

    private void cancelScheduledPoll() {
        if (scheduleId != null && deadlineManager != null) {
            deadlineManager.cancelSchedule(POLL_DEADLINE, scheduleId);
        }
    }
}
