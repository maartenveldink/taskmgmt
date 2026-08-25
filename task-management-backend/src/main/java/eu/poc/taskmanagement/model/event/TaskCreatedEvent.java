package eu.poc.taskmanagement.model.event;

import org.axonframework.eventsourcing.annotation.EventTag;

import eu.poc.taskmanagement.model.TaskType;

import java.time.Instant;
import java.util.List;

/**
 * Published when a new task aggregate is successfully created.
 *
 * <p>This event starts the {@code TaskDeadlineSaga}, which schedules a
 * Quartz job to fire at {@code deadline}.
 *
 * @param taskId        aggregate / correlation ID supplied by the caller
 * @param title         short human-readable label
 * @param description   longer description of the work to be done
 * @param assignedGroup group responsible at creation time; never null
 * @param assignedUser  specific user assigned at creation; null at creation
 * @param deadline      mandatory; used by the Saga to schedule the deadline trigger
 */
public record TaskCreatedEvent(
        @EventTag(key = "taskId") String taskId,
        String title,
        String description,
        String assignedGroup,
        String assignedUser,
        Instant deadline,
        TaskType taskType,
        List<String> expectedExternalUsers
) {}
