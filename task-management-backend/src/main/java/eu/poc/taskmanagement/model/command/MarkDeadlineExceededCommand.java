package eu.poc.taskmanagement.model.command;

import lombok.Builder;
import org.axonframework.modelling.annotation.TargetEntityId;

import java.time.Instant;

/**
 * Internal command issued by {@code TaskDeadlineProcessManager} when a task's
 * deadline elapses while the task is still active.
 *
 * <p>Routing this through the entity (rather than publishing a standalone event)
 * keeps the deadline-exceeded event fully consistent with event sourcing: the
 * entity appends {@code TaskDeadlineExceededEvent} through its normal event
 * stream, guarded by the current (authoritative) task state.
 *
 * @param taskId   the task whose deadline elapsed
 * @param deadline the original deadline that was missed (for the emitted event)
 */
@Builder
public record MarkDeadlineExceededCommand(
        @TargetEntityId String taskId,
        Instant deadline
) {}
