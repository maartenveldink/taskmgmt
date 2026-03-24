package eu.poc.taskmanagement.model.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Assigns (or re-assigns on initial assignment) a task to a user or group.
 *
 * <p>This command is valid when the task is in any non-terminal state.
 * It transitions the task to ASSIGNED status.
 */
public record AssignTaskCommand(
        @TargetAggregateIdentifier String taskId,
        String assigneeName,
        AssigneeType assigneeType
) {}
