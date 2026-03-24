package eu.poc.taskmanagement.model.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Moves a task from ASSIGNED to IN_PROGRESS.
 *
 * <p>Works regardless of whether the current assignee is a user or a group
 * (requirement CH-05).  The task must be in ASSIGNED status.
 */
public record StartTaskCommand(
        @TargetAggregateIdentifier String taskId
) {}
