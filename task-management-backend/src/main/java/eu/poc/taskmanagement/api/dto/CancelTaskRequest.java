package eu.poc.taskmanagement.api.dto;

/** Optional request body for {@code POST /tasks/{id}/cancel}. */
public record CancelTaskRequest(String reason) {}
