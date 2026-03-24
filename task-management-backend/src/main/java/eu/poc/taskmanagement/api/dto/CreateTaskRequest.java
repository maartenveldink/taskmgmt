package eu.poc.taskmanagement.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
        @NotBlank String correlationId,
        @NotBlank String title,
        String description,
        String groupName,
        @NotNull Instant deadline
) {}
