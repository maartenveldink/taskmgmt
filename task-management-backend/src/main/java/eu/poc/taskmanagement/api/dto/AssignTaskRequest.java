package eu.poc.taskmanagement.api.dto;

import eu.poc.taskmanagement.model.command.AssigneeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request body for {@code POST /tasks/{id}/assign}. */
public record AssignTaskRequest(
        @NotBlank String assigneeName,
        @NotNull AssigneeType assigneeType
) {}
