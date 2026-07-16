package eu.poc.taskmanagement.projection.tasks.query;

import eu.poc.taskmanagement.model.TaskStatus;

import java.time.Instant;

/**
 * Fetches all tasks, with optional filters.
 *
 * @param status         if non-null, only tasks in this status are returned
 * @param deadlineBefore if non-null, only tasks whose deadline is before this instant
 * @param deadlineAfter  if non-null, only tasks whose deadline is after this instant
 * @param offset         zero-based row offset
 * @param limit          page size
 */
public record GetAllTasksQuery(
        TaskStatus status,
        Instant deadlineBefore,
        Instant deadlineAfter,
        int offset,
        int limit
) {}
