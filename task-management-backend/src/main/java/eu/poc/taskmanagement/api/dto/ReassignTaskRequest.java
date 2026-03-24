package eu.poc.taskmanagement.api.dto;

import eu.poc.taskmanagement.model.command.AssigneeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request body for {@code POST /tasks/{id}/reassign}. */
public record ReassignTaskRequest(
        @NotBlank String newAssigneeName,
        @NotNull AssigneeType newAssigneeType
) {}
