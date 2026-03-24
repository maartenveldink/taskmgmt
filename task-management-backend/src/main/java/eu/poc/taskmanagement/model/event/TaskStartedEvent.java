package eu.poc.taskmanagement.model.event;

import eu.poc.taskmanagement.model.TaskStatus;

/**
 * Published when a task transitions from ASSIGNED to IN_PROGRESS.
 *
 * @param taskId         aggregate ID
 * @param previousStatus always ASSIGNED when this event is valid
 */
public record TaskStartedEvent(
        String taskId,
        TaskStatus previousStatus
) {}
