package eu.poc.taskmanagement.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * HTTP request body for {@code POST /tasks}.
 *
 * <p>{@code correlationId} is used as the Axon aggregate identifier.  Supply a
 * stable, caller-owned UUID so that the external system can retry safely without
 * creating duplicate tasks (idempotency — requirement CH-11).
 *
 * <p>{@code groupName} is optional; if omitted the system uses the default group
 * configured via {@code task.default-group} in {@code application.yaml}
 * (currently {@code "unassigned"}).
 */
public record CreateTaskRequest(
        @NotBlank @Size(max = 100) String correlationId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String description,
        @Size(max = 100) String groupName,
        @NotNull @Future Instant deadline
) {}
