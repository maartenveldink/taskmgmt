package eu.poc.taskmanagement.model.event;

import eu.poc.taskmanagement.model.TaskStatus;

/**
 * Published when a task transitions from IN_PROGRESS to DONE (terminal).
 *
 * <p>The {@code TaskDeadlineSaga} listens for this event to cancel the
 * pending Quartz deadline trigger.
 *
 * @param taskId         aggregate ID
 * @param previousStatus always IN_PROGRESS when this event is valid
 */
public record TaskCompletedEvent(
        String taskId,
        TaskStatus previousStatus
) {}
