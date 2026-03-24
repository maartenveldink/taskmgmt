package eu.poc.taskmanagement.model.event;

import eu.poc.taskmanagement.model.TaskStatus;

import java.time.Instant;

/**
 * Published by {@code TaskDeadlineSaga} when the Quartz deadline fires and
 * the task has not yet reached a terminal state.
 *
 * <p>This event is intended for future consumption by external systems (e.g.,
 * a notification service).  For this PoC the primary escalation action is a
 * WARN log entry (see {@code TaskDeadlineSaga#onDeadline}).
 *
 * <p>The {@code AuditTrailProjection} records this event so that the full
 * lifecycle of a late task is visible in the audit trail (requirement AT-06).
 *
 * @param taskId         aggregate ID of the late task
 * @param deadline       the original deadline that was missed
 * @param statusAtBreach the task's status at the moment the deadline fired
 */
public record TaskDeadlineExceededEvent(
        String taskId,
        Instant deadline,
        TaskStatus statusAtBreach
) {}
