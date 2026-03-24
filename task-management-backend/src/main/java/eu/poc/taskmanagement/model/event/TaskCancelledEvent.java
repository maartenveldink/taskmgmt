package eu.poc.taskmanagement.model.event;

import eu.poc.taskmanagement.model.TaskStatus;

/**
 * Published when a task is cancelled (transitions to CANCELLED terminal state).
 *
 * <p>The {@code TaskDeadlineSaga} listens for this event to cancel the
 * pending Quartz deadline trigger — a cancelled task is in a terminal state
 * so the deadline is no longer relevant.
 *
 * @param taskId         aggregate ID
 * @param previousStatus the status before cancellation
 * @param reason         optional human-readable reason for cancellation
 */
public record TaskCancelledEvent(
        String taskId,
        TaskStatus previousStatus,
        String reason
) {}
