package eu.poc.taskmanagement.api;

import eu.poc.taskmanagement.model.command.AssignTaskCommand;
import eu.poc.taskmanagement.model.command.AssigneeType;

/**
 * Test data builder for AssignTaskCommand.
 * Provides sensible defaults and fluent configuration.
 */
public class AssignTaskCommandTestDataBuilder {

    private String taskId;
    private String assigneeName = "alice";
    private AssigneeType assigneeType = AssigneeType.USER;

    public static AssignTaskCommandTestDataBuilder anAssignTaskCommand() {
        return new AssignTaskCommandTestDataBuilder();
    }

    public AssignTaskCommandTestDataBuilder withTaskId(String taskId) {
        this.taskId = taskId;
        return this;
    }

    public AssignTaskCommandTestDataBuilder withAssigneeName(String assigneeName) {
        this.assigneeName = assigneeName;
        return this;
    }

    public AssignTaskCommandTestDataBuilder withAssigneeType(AssigneeType assigneeType) {
        this.assigneeType = assigneeType;
        return this;
    }

    public AssignTaskCommandTestDataBuilder assignToUser(String userName) {
        this.assigneeName = userName;
        this.assigneeType = AssigneeType.USER;
        return this;
    }

    public AssignTaskCommandTestDataBuilder assignToGroup(String groupName) {
        this.assigneeName = groupName;
        this.assigneeType = AssigneeType.GROUP;
        return this;
    }

    public AssignTaskCommand build() {
        if (taskId == null) {
            throw new IllegalStateException("taskId is required");
        }
        return new AssignTaskCommand(taskId, assigneeName, assigneeType);
    }
}
