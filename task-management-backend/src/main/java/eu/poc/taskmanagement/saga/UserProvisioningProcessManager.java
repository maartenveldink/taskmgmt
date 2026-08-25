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
 * from the {@code ScheduledExecutorService} scheduler thread; persisting it means
 * the database provides isolation and optimistic locking between those threads
 * (and the state survives a restart).  Every access below therefore runs inside a
 * JTA transaction — the {@code @EventHandler} methods via {@link Transactional},
 * the scheduler-thread {@link #poll(String)} via {@link TransactionRunner}.
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
    // Poll call-back (runs on a scheduler thread)
    // =========================================================================

    private void poll(String taskId) {
        try {
            // No ambient transaction on the scheduler thread — open a fresh one so
            // the state read/write and any resulting event-store append commit
            // durably and atomically.
            transactionRunner.runInTransaction(() -> doPoll(taskId));
        } catch (RuntimeException e) {
            // Never let an exception kill the scheduler thread. A concurrent
            // terminal event may have removed/updated the row (optimistic-lock
            // conflict); that path already handles completion, so we simply stop.
            log.warn("Provisioning poll failed for taskId={}; stopping polling for this task", taskId, e);
        }
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
                Instant.now().plusSeconds(POLL_INTERVAL_SECONDS), () -> poll(taskId));
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
