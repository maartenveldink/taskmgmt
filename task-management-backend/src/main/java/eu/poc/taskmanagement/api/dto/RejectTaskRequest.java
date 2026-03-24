package eu.poc.taskmanagement.api.dto;

/** Optional request body for {@code POST /tasks/{id}/reject}. */
public record RejectTaskRequest(String reason) {}
