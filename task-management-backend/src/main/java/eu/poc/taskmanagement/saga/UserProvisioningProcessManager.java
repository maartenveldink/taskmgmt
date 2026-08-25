package eu.poc.taskmanagement.saga;

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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User-provisioning completion process manager.
 *
 * <h2>Purpose</h2>
 * Replaces the Axon 4 {@code UserProvisioningCompletionSaga}.  For tasks of type
 * {@link TaskType#USER_PROVISIONING} it periodically polls an external user
 * directory until all expected users exist, then completes the task.
 *
 * <h2>Lifecycle</h2>
 * <pre>
 *   TaskCreatedEvent (USER_PROVISIONING, non-empty expected users) → start
 *   TaskStartedEvent  → begin polling every {@value #POLL_INTERVAL_SECONDS}s
 *   poll fires        → all expected users present?  → CompleteTaskCommand + end
 *                       past deadline?               → time out + end
 *                       otherwise                    → reschedule next poll
 *   terminal event    → cancel polling + end
 * </pre>
 */
@Slf4j
@ApplicationScoped
public class UserProvisioningProcessManager {

    private static final long POLL_INTERVAL_SECONDS = 5L;

    private static final class ProvisioningState {
        // Written on the event-processor thread, read on the scheduler thread.
        // volatile establishes the happens-before needed for safe cross-thread reads.
        private volatile Instant deadline;
        private volatile TaskStatus lastKnownStatus;
        private volatile Set<String> expectedUsers;
        private volatile String scheduleId;
    }

    private final Map<String, ProvisioningState> states = new ConcurrentHashMap<>();

    private final DeadlineScheduler scheduler;
    private final ExternalUserDirectoryClient externalUserDirectoryClient;
    private final CommandGateway commandGateway;
    private final TransactionRunner transactionRunner;

    @Inject
    public UserProvisioningProcessManager(DeadlineScheduler scheduler,
                                          ExternalUserDirectoryClient externalUserDirectoryClient,
                                          CommandGateway commandGateway,
                                          TransactionRunner transactionRunner) {
        this.scheduler = scheduler;
        this.externalUserDirectoryClient = externalUserDirectoryClient;
        this.commandGateway = commandGateway;
        this.transactionRunner = transactionRunner;
    }

    // =========================================================================
    // Event handlers
    // =========================================================================

    @EventHandler
    public void on(TaskCreatedEvent event) {
        if (event.taskType() != TaskType.USER_PROVISIONING) {
            return;
        }
        if (event.expectedExternalUsers() == null || event.expectedExternalUsers().isEmpty()) {
            log.warn("Not starting user provisioning process for taskId={} because expectedExternalUsers is empty",
                    event.taskId());
            return;
        }
        ProvisioningState state = new ProvisioningState();
        state.deadline = event.deadline();
        state.lastKnownStatus = TaskStatus.CREATED;
        state.expectedUsers = new HashSet<>(event.expectedExternalUsers());
        states.put(event.taskId(), state);
    }

    @EventHandler
    public void on(TaskAssignedEvent event) {
        updateStatus(event.taskId(), TaskStatus.ASSIGNED);
    }

    @EventHandler
    public void on(TaskStartedEvent event) {
        ProvisioningState state = states.get(event.taskId());
        if (state == null) {
            return;
        }
        state.lastKnownStatus = TaskStatus.IN_PROGRESS;
        scheduleNextPoll(event.taskId(), state);
    }

    @EventHandler
    public void on(TaskCompletedEvent event) {
        end(event.taskId(), TaskStatus.DONE);
    }

    @EventHandler
    public void on(TaskCancelledEvent event) {
        end(event.taskId(), TaskStatus.CANCELLED);
    }

    @EventHandler
    public void on(TaskRejectedEvent event) {
        end(event.taskId(), TaskStatus.REJECTED);
    }

    // =========================================================================
    // Poll call-back (runs on a scheduler thread)
    // =========================================================================

    private void poll(String taskId) {
        ProvisioningState state = states.get(taskId);
        if (state == null) {
            return;
        }
        if (state.lastKnownStatus == null || state.lastKnownStatus.isTerminal()) {
            states.remove(taskId);
            return;
        }
        if (Instant.now().isAfter(state.deadline)) {
            log.warn("User provisioning completion process timed out for taskId={}, expectedUsers={}",
                    taskId, state.expectedUsers);
            states.remove(taskId);
            return;
        }
        if (state.lastKnownStatus != TaskStatus.IN_PROGRESS) {
            scheduleNextPoll(taskId, state);
            return;
        }

        Set<String> createdUsers = externalUserDirectoryClient.fetchCreatedUsers(taskId);
        if (createdUsers.containsAll(state.expectedUsers)) {
            // Runs on a scheduler thread with no ambient transaction — open a fresh
            // transaction so the resulting TaskCompletedEvent is committed durably
            // to the event store.
            transactionRunner.runInTransaction(() ->
                    commandGateway.sendAndWait(new CompleteTaskCommand(taskId)));
            states.remove(taskId);
            return;
        }

        scheduleNextPoll(taskId, state);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void scheduleNextPoll(String taskId, ProvisioningState state) {
        state.scheduleId = scheduler.schedule(
                Instant.now().plusSeconds(POLL_INTERVAL_SECONDS), () -> poll(taskId));
    }

    private void updateStatus(String taskId, TaskStatus status) {
        ProvisioningState state = states.get(taskId);
        if (state != null) {
            state.lastKnownStatus = status;
        }
    }

    private void end(String taskId, TaskStatus terminalStatus) {
        ProvisioningState state = states.remove(taskId);
        if (state != null) {
            state.lastKnownStatus = terminalStatus;
            scheduler.cancel(state.scheduleId);
        }
    }
}
