package eu.poc.taskmanagement.model.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Marks a task as DONE.  The task must be IN_PROGRESS.
 *
 * <p>Completing a task also terminates the deadline Saga, preventing a
 * spurious escalation log entry.
 */
public record CompleteTaskCommand(
        @TargetAggregateIdentifier String taskId
) {}
