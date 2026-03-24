package eu.poc.taskmanagement.model.event;

/**
 * Published when a task is reassigned to a different user or group without
 * a status change (e.g., a group member claiming a task from the queue).
 *
 * <p>Null values indicate the slot was unset before or after the reassignment.
 *
 * @param taskId        aggregate ID
 * @param previousGroup group responsible before reassignment
 * @param previousUser  user assigned before reassignment
 * @param assignedGroup group responsible after reassignment
 * @param assignedUser  user assigned after reassignment
 */
public record TaskReassignedEvent(
        String taskId,
        String previousGroup,
        String previousUser,
        String assignedGroup,
        String assignedUser
) {}
