package eu.poc.taskmanagement.model.command;

import lombok.Builder;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * Marks a task as DONE.  The task must be IN_PROGRESS.
 *
 * <p>Completing a task also terminates the deadline Saga, preventing a
 * spurious escalation log entry.
 */
@Builder
public record CompleteTaskCommand(
        @TargetEntityId String taskId
) {}
