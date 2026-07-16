package eu.poc.taskmanagement.projection.tasks.query;

import eu.poc.taskmanagement.model.TaskStatus;

import java.time.Instant;

/**
 * Fetches tasks assigned to a specific user group, with optional filters.
 *
 * @param groupName      name of the group (stored as a plain string)
 * @param status         optional status filter
 * @param deadlineBefore optional upper bound on task deadline
 * @param deadlineAfter  optional lower bound on task deadline
 * @param offset         zero-based row offset
 * @param limit          page size
 */
public record GetTasksByGroupQuery(
        String groupName,
        TaskStatus status,
        Instant deadlineBefore,
        Instant deadlineAfter,
        int offset,
        int limit
) {}
