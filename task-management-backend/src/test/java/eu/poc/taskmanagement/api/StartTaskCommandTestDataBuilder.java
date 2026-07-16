package eu.poc.taskmanagement.api;

import eu.poc.taskmanagement.model.command.StartTaskCommand;

/**
 * Test data builder for StartTaskCommand.
 * Provides sensible defaults and fluent configuration.
 */
public class StartTaskCommandTestDataBuilder {

    private String taskId;

    public static StartTaskCommandTestDataBuilder aStartTaskCommand() {
        return new StartTaskCommandTestDataBuilder();
    }

    public StartTaskCommandTestDataBuilder withTaskId(String taskId) {
        this.taskId = taskId;
        return this;
    }

    public StartTaskCommand build() {
        if (taskId == null) {
            throw new IllegalStateException("taskId is required");
        }
        return new StartTaskCommand(taskId);
    }
}
