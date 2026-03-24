package eu.poc.taskmanagement.model.command;

/**
 * Distinguishes whether a task is assigned to an individual user or to a
 * user group.
 *
 * <p>A task starts life assigned to a GROUP (the configured default group if
 * none is specified).  It can later be reassigned to a specific USER within
 * that group, or to any other group or user.
 */
public enum AssigneeType {
    USER,
    GROUP
}
