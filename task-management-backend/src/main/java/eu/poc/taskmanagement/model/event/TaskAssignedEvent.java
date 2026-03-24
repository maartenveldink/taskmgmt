package eu.poc.taskmanagement.model.event;

/**
 * Published when a task is explicitly assigned via {@code AssignTaskCommand}.
 *
 * <p>Carries both the new and previous assignment so the audit trail can record
 * the full transition (requirement AT-04).  A task can be assigned to a group
 * and a user simultaneously; a null value means that slot is unset.
 *
 * @param taskId        aggregate ID
 * @param assignedGroup group now responsible (null if unchanged and was unset)
 * @param assignedUser  specific user now assigned (null if unchanged and was unset)
 * @param previousGroup group responsible before this assignment
 * @param previousUser  user assigned before this assignment
 */
public record TaskAssignedEvent(
        String taskId,
        String assignedGroup,
        String assignedUser,
        String previousGroup,
        String previousUser
) {}
