package eu.poc.taskmanagement.projection.audittrail.query;

/**
 * Fetches the full audit trail for a single task, ordered chronologically
 * (oldest event first).
 *
 * @param taskId the aggregate ID of the task to inspect
 */
public record GetAuditTrailByTaskQuery(String taskId) {}
