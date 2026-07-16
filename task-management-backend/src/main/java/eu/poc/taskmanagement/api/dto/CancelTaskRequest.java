package eu.poc.taskmanagement.api.dto;

import jakarta.validation.constraints.Size;

/** Optional request body for {@code POST /tasks/{id}/cancel}. */
public record CancelTaskRequest(@Size(max = 1000) String reason) {}
