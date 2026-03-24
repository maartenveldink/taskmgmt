package eu.poc.taskmanagement.model.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Reassigns a task to a different user or group without changing its status.
 *
 * <p>Valid when the task is in any non-terminal state.  Can be used to move a
 * task from a group to a specific user within that group (e.g., a team member
 * "claiming" a task from the shared queue).
 */
public record ReassignTaskCommand(
        @TargetAggregateIdentifier String taskId,
        String newAssigneeName,
        AssigneeType newAssigneeType
) {}
