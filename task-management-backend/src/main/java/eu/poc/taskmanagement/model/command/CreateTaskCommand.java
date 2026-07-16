package eu.poc.taskmanagement.model.command;

import lombok.Builder;
import eu.poc.taskmanagement.model.TaskType;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.time.Instant;
import java.util.List;

/**
 * Issued by an external system to create a new task.
 *
 * <p><strong>taskId</strong> — the caller-supplied correlation ID used as the
 * Axon aggregate identifier.  Idempotency is guaranteed: a duplicate command
 * carrying an already-known taskId is rejected with a conflict error.
 *
 * <p><strong>groupName</strong> — initial user group.  If null, the REST layer
 * substitutes the value of {@code task.default-group} (application.yaml)
 * before dispatching this command, so the aggregate always receives a
 * non-null group name.
 *
 * <p><strong>deadline</strong> — mandatory; the Saga uses this to schedule the
 * Quartz-backed deadline trigger (see {@code TaskDeadlineSaga}).
 */
@Builder(toBuilder = true)
public record CreateTaskCommand(
        @TargetAggregateIdentifier String taskId,
        String title,
        String description,
        String groupName,
        Instant deadline,
        TaskType taskType,
        List<String> expectedExternalUsers
) {}
