package eu.poc.taskmanagement.model.event;

import org.axonframework.eventsourcing.annotation.EventTag;

import eu.poc.taskmanagement.model.TaskStatus;

/**
 * Published when a task is rejected (transitions to REJECTED terminal state).
 *
 * <p>Only valid from CREATED or ASSIGNED status.  The {@code TaskDeadlineSaga}
 * listens for this event to cancel the pending deadline trigger.
 *
 * @param taskId         aggregate ID
 * @param previousStatus the status before rejection
 * @param reason         optional human-readable reason for rejection
 */
public record TaskRejectedEvent(
        @EventTag(key = "taskId") String taskId,
        TaskStatus previousStatus,
        String reason
) {}
