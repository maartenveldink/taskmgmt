package eu.poc.taskmanagement.projection.tasks.query;

import eu.poc.taskmanagement.model.TaskStatus;

import java.time.Instant;

/**
 * Fetches tasks assigned to a specific user, with optional filters.
 *
 * @param userName       name of the user (stored as a plain string, no user management)
 * @param status         optional status filter
 * @param deadlineBefore optional upper bound on task deadline
 * @param deadlineAfter  optional lower bound on task deadline
 */
public record GetTasksByUserQuery(
        String userName,
        TaskStatus status,
        Instant deadlineBefore,
        Instant deadlineAfter
) {}
