package eu.poc.taskmanagement.api.dto;

import eu.poc.taskmanagement.model.command.AssigneeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /tasks/{id}/reassign}. */
public record ReassignTaskRequest(
        @NotBlank @Size(max = 100) String newAssigneeName,
        @NotNull AssigneeType newAssigneeType
) {}
