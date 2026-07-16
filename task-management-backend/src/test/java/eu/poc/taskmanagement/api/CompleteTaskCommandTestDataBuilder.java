package eu.poc.taskmanagement.api;

import eu.poc.taskmanagement.model.command.CompleteTaskCommand;

/**
 * Test data builder for CompleteTaskCommand.
 * Provides sensible defaults and fluent configuration.
 */
public class CompleteTaskCommandTestDataBuilder {

    private String taskId;

    public static CompleteTaskCommandTestDataBuilder aCompleteTaskCommand() {
        return new CompleteTaskCommandTestDataBuilder();
    }

    public CompleteTaskCommandTestDataBuilder withTaskId(String taskId) {
        this.taskId = taskId;
        return this;
    }

    public CompleteTaskCommand build() {
        if (taskId == null) {
            throw new IllegalStateException("taskId is required");
        }
        return new CompleteTaskCommand(taskId);
    }
}
