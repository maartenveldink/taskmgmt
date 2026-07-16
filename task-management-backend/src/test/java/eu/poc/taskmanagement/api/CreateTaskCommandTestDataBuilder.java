package eu.poc.taskmanagement.api;

import eu.poc.taskmanagement.model.command.CreateTaskCommand;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Test data builder for CreateTaskCommand.
 * Provides sensible defaults and fluent configuration.
 */
public class CreateTaskCommandTestDataBuilder {

    private String taskId = "task-" + UUID.randomUUID();
    private String title = "Test Task";
    private String description = "Default test task";
    private String groupName = "ops-team";
    private Instant deadline = Instant.now().plus(30, ChronoUnit.DAYS);

    public static CreateTaskCommandTestDataBuilder aCreateTaskCommand() {
        return new CreateTaskCommandTestDataBuilder();
    }

    public CreateTaskCommandTestDataBuilder withTaskId(String taskId) {
        this.taskId = taskId;
        return this;
    }

    public CreateTaskCommandTestDataBuilder withTitle(String title) {
        this.title = title;
        return this;
    }

    public CreateTaskCommandTestDataBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    public CreateTaskCommandTestDataBuilder withGroupName(String groupName) {
        this.groupName = groupName;
        return this;
    }

    public CreateTaskCommandTestDataBuilder withDeadline(Instant deadline) {
        this.deadline = deadline;
        return this;
    }

    public CreateTaskCommandTestDataBuilder withDeadlineInSeconds(long seconds) {
        this.deadline = Instant.now().plus(seconds, ChronoUnit.SECONDS);
        return this;
    }

    public CreateTaskCommand build() {
        return new CreateTaskCommand(taskId, title, description, groupName, deadline);
    }
}
