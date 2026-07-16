package eu.poc.taskmanagement.saga;

import java.time.Instant;

public record UserProvisioningPollPayload(
        String taskId,
        Instant deadline
) {}
