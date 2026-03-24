package eu.poc.taskmanagement.model.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Rejects a task (transitions to REJECTED terminal state).
 *
 * <p>Only valid when the task is in CREATED or ASSIGNED status.  Typically
 * used to refuse an invalid or duplicate request from the external system.
 * The optional {@code reason} is recorded in the event and the audit trail.
 */
public record RejectTaskCommand(
        @TargetAggregateIdentifier String taskId,
        String reason
) {}
