package eu.poc.taskmanagement.api.dto;

import jakarta.validation.constraints.Size;

/** Optional request body for {@code POST /tasks/{id}/reject}. */
public record RejectTaskRequest(@Size(max = 1000) String reason) {}
