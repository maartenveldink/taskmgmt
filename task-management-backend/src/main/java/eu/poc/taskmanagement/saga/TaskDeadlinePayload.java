package eu.poc.taskmanagement.saga;

import java.time.Instant;

/**
 * Serialisable payload attached to the Quartz deadline job.
 *
 * <p>When the Quartz job fires, Axon's {@code QuartzDeadlineManager}
 * deserialises this payload and delivers it to the {@code @DeadlineHandler}
 * in {@code TaskDeadlineSaga}.  It must be Jackson-serialisable.
 *
 * @param taskId   the task whose deadline has fired
 * @param deadline the original deadline timestamp (for logging / audit)
 */
public record TaskDeadlinePayload(
        String taskId,
        Instant deadline
) {}
