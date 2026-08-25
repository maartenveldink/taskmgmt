package eu.poc.taskmanagement.model.command;

import lombok.Builder;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * Moves a task from ASSIGNED to IN_PROGRESS.
 *
 * <p>Works regardless of whether the current assignee is a user or a group
 * (requirement CH-05).  The task must be in ASSIGNED status.
 */
@Builder
public record StartTaskCommand(
        @TargetEntityId String taskId
) {}
