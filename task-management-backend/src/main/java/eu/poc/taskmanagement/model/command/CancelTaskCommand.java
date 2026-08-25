package eu.poc.taskmanagement.model.command;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * Cancels a task (transitions to CANCELLED terminal state).
 *
 * <p>Valid when the task is in any non-terminal state.  The optional
 * {@code reason} is recorded in the event and the audit trail.
 */
public record CancelTaskCommand(
        @TargetEntityId String taskId,
        String reason
) {}
