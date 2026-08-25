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
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * User-provisioning completion process manager.
 *
 * <h2>Purpose</h2>
 * Replaces the Axon 4 {@code UserProvisioningCompletionSaga}.  For tasks of type
 * {@link TaskType#USER_PROVISIONING} it periodically polls an external user
 * directory until all expected users exist, then completes the task.
 *
 * <h2>State</h2>
 * Per-task process state is a transactional database row ({@link ProvisioningState}),
 * not in-memory.  The state is written on the Axon event-processor thread and read
 * when a poll fires; persisting it means the database provides isolation and
 * optimistic locking between those threads (and the state survives a restart).
 *
 * <h2>Durable, cluster-safe polling</h2>
 * Each poll is a durable {@link ScheduledJob} scheduled through the
 * {@link DeadlineScheduler}.  The {@link PersistentDeadlineScheduler} runs
 * {@link #execute(String, Instant)} inside a fresh JTA transaction (so the state
 * read/write and any resulting event-store append commit durably and atomically),
 * and guarantees that a due poll is claimed and run by exactly one node.  Because
 * the poll is a database row, a pending poll also survives a restart.
 *
 * <h2>Lifecycle</h2>
 * <pre>
 *   TaskCreatedEvent (USER_PROVISIONING, non-empty expected users) → persist state
 *   TaskStartedEvent  → begin polling every {@value #POLL_INTERVAL_SECONDS}s
 *   poll fires        → all expected users present?  → CompleteTaskCommand + end
 *                       past deadline?               → time out + end
 *                       otherwise                    → reschedule next poll
 *   terminal event    → cancel polling + end
 * </pre>
 */
@Slf4j
@ApplicationScoped
public class UserProvisioningProcessManager implements ScheduledJobHandler {

    private static final long POLL_INTERVAL_SECONDS = 5L;

    private final DeadlineScheduler scheduler;
    private final ExternalUserDirectoryClient externalUserDirectoryClient;
    private final CommandGateway commandGateway;

    @Inject
    public UserProvisioningProcessManager(DeadlineScheduler scheduler,
                                          ExternalUserDirectoryClient externalUserDirectoryClient,
                                          CommandGateway commandGateway) {
        this.scheduler = scheduler;
        this.externalUserDirectoryClient = externalUserDirectoryClient;
        this.commandGateway = commandGateway;
    }

    // =========================================================================
    // Event handlers (event-processor thread, transactional)
    // =========================================================================

    @EventHandler
    @Transactional
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
        state.taskId = event.taskId();
        state.deadline = event.deadline();
        state.lastKnownStatus = TaskStatus.CREATED;
        state.setExpectedUsers(new HashSet<>(event.expectedExternalUsers()));
        state.persist();
    }

    @EventHandler
    @Transactional
    public void on(TaskAssignedEvent event) {
        updateStatus(event.taskId(), TaskStatus.ASSIGNED);
    }

    @EventHandler
    @Transactional
    public void on(TaskStartedEvent event) {
        ProvisioningState state = ProvisioningState.findById(event.taskId());
        if (state == null) {
            return;
        }
        state.lastKnownStatus = TaskStatus.IN_PROGRESS;
        scheduleNextPoll(state);
    }

    @EventHandler
    @Transactional
    public void on(TaskCompletedEvent event) {
        end(event.taskId());
    }

    @EventHandler
    @Transactional
    public void on(TaskCancelledEvent event) {
        end(event.taskId());
    }

    @EventHandler
    @Transactional
    public void on(TaskRejectedEvent event) {
        end(event.taskId());
    }

    // =========================================================================
    // Scheduled-job handler (runs inside a fresh tx opened by the scheduler)
    // =========================================================================

    @Override
    public ScheduledJobType type() {
        return ScheduledJobType.PROVISIONING_POLL;
    }

    @Override
    public void execute(String taskId, Instant fireAt) {
        doPoll(taskId);
    }

    private void doPoll(String taskId) {
        ProvisioningState state = ProvisioningState.findById(taskId);
        if (state == null) {
            return;
        }
        if (state.lastKnownStatus == null || state.lastKnownStatus.isTerminal()) {
            state.delete();
            return;
        }
        if (Instant.now().isAfter(state.deadline)) {
            log.warn("User provisioning completion process timed out for taskId={}, expectedUsers={}",
                    taskId, state.getExpectedUsers());
            state.delete();
            return;
        }
        if (state.lastKnownStatus != TaskStatus.IN_PROGRESS) {
            scheduleNextPoll(state);
            return;
        }

        Set<String> createdUsers = externalUserDirectoryClient.fetchCreatedUsers(taskId);
        if (createdUsers.containsAll(state.getExpectedUsers())) {
            // Dispatch within this same transaction. The resulting TaskCompletedEvent
            // is handled synchronously by on(TaskCompletedEvent) → end(), which
            // removes the row, so we must not delete it again here.
            commandGateway.sendAndWait(new CompleteTaskCommand(taskId));
            return;
        }

        scheduleNextPoll(state);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void scheduleNextPoll(ProvisioningState state) {
        String taskId = state.taskId;
        state.scheduleId = scheduler.schedule(
                Instant.now().plusSeconds(POLL_INTERVAL_SECONDS), ScheduledJobType.PROVISIONING_POLL, taskId);
    }

    private void updateStatus(String taskId, TaskStatus status) {
        ProvisioningState state = ProvisioningState.findById(taskId);
        if (state != null) {
            state.lastKnownStatus = status;
        }
    }

    private void end(String taskId) {
        ProvisioningState state = ProvisioningState.findById(taskId);
        if (state != null) {
            if (state.scheduleId != null) {
                scheduler.cancel(state.scheduleId);
            }
            state.delete();
        }
    }
}
