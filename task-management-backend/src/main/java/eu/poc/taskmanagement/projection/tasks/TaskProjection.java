package eu.poc.taskmanagement.projection.tasks;

import eu.poc.taskmanagement.model.TaskStatus;
import eu.poc.taskmanagement.model.event.*;
import eu.poc.taskmanagement.projection.tasks.query.GetAllTasksQuery;
import eu.poc.taskmanagement.projection.tasks.query.GetTasksByGroupQuery;
import eu.poc.taskmanagement.projection.tasks.query.GetTasksByUserQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.Timestamp;
import org.axonframework.queryhandling.QueryHandler;

import java.time.Instant;
import java.util.List;

/**
 * Read-model projection — query side.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Listen to all domain events and maintain the {@code TaskView} JPA table
 *       so it always reflects the latest state of every task.</li>
 *   <li>Answer {@code GetAllTasksQuery}, {@code GetTasksByUserQuery} and
 *       {@code GetTasksByGroupQuery} from the read-model table.</li>
 * </ul>
 *
 * <h2>Consistency model</h2>
 * Because Axon is configured with a {@code SubscribingEventProcessor}, events
 * are delivered to this projection <em>synchronously within the same JTA
 * transaction</em> as the command that produced them.  The read model is
 * therefore always consistent with the command side after each command completes.
 *
 * <h2>Replay</h2>
 * To rebuild this projection from scratch:
 * <ol>
 *   <li>Truncate the {@code task_view} table.</li>
 *   <li>Replay all events from the Axon {@code EmbeddedEventStore} in order.</li>
 * </ol>
 * Each {@code @EventHandler} method is idempotent (upsert semantics), so
 * replaying the same event twice is safe (requirement EH-10).
 */
@Slf4j
@ApplicationScoped
public class TaskProjection {

    // =========================================================================
    // Event Handlers — build / update the read model
    // =========================================================================

    @EventHandler
    @Transactional
    public void on(TaskCreatedEvent event, @Timestamp Instant timestamp) {
        log.debug("Projection: TaskCreatedEvent for taskId={}", event.taskId());

        // Upsert: use findByIdOptional so replay is safe.
        TaskView view = TaskView.findById(event.taskId());
        if (view == null) {
            view = new TaskView();
            view.taskId = event.taskId();
            view.createdAt = timestamp;
        }
        view.title = event.title();
        view.description = event.description();
        view.assignedGroup = event.assignedGroup();
        view.assignedUser = event.assignedUser();
        view.status = TaskStatus.CREATED;
        view.deadline = event.deadline();
        view.updatedAt = timestamp;
        view.persistAndFlush();
    }

    @EventHandler
    @Transactional
    public void on(TaskAssignedEvent event, @Timestamp Instant timestamp) {
        log.debug("Projection: TaskAssignedEvent for taskId={}", event.taskId());
        TaskView view = requireTaskView(event.taskId());
        view.assignedGroup = event.assignedGroup();
        view.assignedUser = event.assignedUser();
        view.status = TaskStatus.ASSIGNED;
        view.updatedAt = timestamp;
    }

    @EventHandler
    @Transactional
    public void on(TaskReassignedEvent event, @Timestamp Instant timestamp) {
        log.debug("Projection: TaskReassignedEvent for taskId={}", event.taskId());
        TaskView view = requireTaskView(event.taskId());
        view.assignedGroup = event.assignedGroup();
        view.assignedUser = event.assignedUser();
        view.updatedAt = timestamp;
    }

    @EventHandler
    @Transactional
    public void on(TaskStartedEvent event, @Timestamp Instant timestamp) {
        log.debug("Projection: TaskStartedEvent for taskId={}", event.taskId());
        updateStatus(event.taskId(), TaskStatus.IN_PROGRESS, timestamp);
    }

    @EventHandler
    @Transactional
    public void on(TaskCompletedEvent event, @Timestamp Instant timestamp) {
        log.debug("Projection: TaskCompletedEvent for taskId={}", event.taskId());
        updateStatus(event.taskId(), TaskStatus.DONE, timestamp);
    }

    @EventHandler
    @Transactional
    public void on(TaskCancelledEvent event, @Timestamp Instant timestamp) {
        log.debug("Projection: TaskCancelledEvent for taskId={}", event.taskId());
        updateStatus(event.taskId(), TaskStatus.CANCELLED, timestamp);
    }

    @EventHandler
    @Transactional
    public void on(TaskRejectedEvent event, @Timestamp Instant timestamp) {
        log.debug("Projection: TaskRejectedEvent for taskId={}", event.taskId());
        updateStatus(event.taskId(), TaskStatus.REJECTED, timestamp);
    }

    // =========================================================================
    // Query Handlers
    // =========================================================================

    @QueryHandler
    @Transactional
    public List<TaskView> handle(GetAllTasksQuery query) {
        return TaskView.findAllFiltered(
                query.status(), query.deadlineBefore(), query.deadlineAfter(),
                query.offset(), query.limit());
    }

    @QueryHandler
    @Transactional
    public List<TaskView> handle(GetTasksByUserQuery query) {
        return TaskView.findByUser(query.userName(), query.status(),
                query.deadlineBefore(), query.deadlineAfter(),
                query.offset(), query.limit());
    }

    @QueryHandler
    @Transactional
    public List<TaskView> handle(GetTasksByGroupQuery query) {
        return TaskView.findByGroup(query.groupName(), query.status(),
                query.deadlineBefore(), query.deadlineAfter(),
                query.offset(), query.limit());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void updateStatus(String taskId, TaskStatus newStatus, Instant timestamp) {
        TaskView view = requireTaskView(taskId);
        view.status = newStatus;
        view.updatedAt = timestamp;
    }

    private TaskView requireTaskView(String taskId) {
        TaskView view = TaskView.findById(taskId);
        if (view == null) {
            log.warn("Projection: TaskView not found for taskId={} — event may have arrived out of order", taskId);
            throw new IllegalStateException("TaskView not found for taskId=" + taskId);
        }
        return view;
    }
}
