package eu.poc.taskmanagement.testdata;

import eu.poc.taskmanagement.model.command.AssignTaskCommand;
import eu.poc.taskmanagement.model.command.AssignTaskCommand.AssignTaskCommandBuilder;
import eu.poc.taskmanagement.model.command.AssigneeType;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

/**
 * Test data builder for AssignTaskCommand.
 * Delegates to Lombok's generated builder, adding factory methods
 * for common test scenarios.
 */
@RequiredArgsConstructor(staticName = "from")
public class AssignTaskCommandTestDataBuilder {

    @Delegate
    private final AssignTaskCommandBuilder delegatedBuilder;

    /**
     * Create a functionally valid command with sensible test defaults.
     * Assigns to a user named "alice".
     *
     * @return AssignTaskCommandTestDataBuilder
     */
    public static AssignTaskCommandTestDataBuilder valid() {
        AssignTaskCommandBuilder builder = AssignTaskCommand.builder()
                .taskId("task-" + System.nanoTime())
                .assigneeName("alice")
                .assigneeType(AssigneeType.USER);

        return new AssignTaskCommandTestDataBuilder(builder);
    }

    public AssignTaskCommandTestDataBuilder taskId(String taskId) {
        delegatedBuilder.taskId(taskId);
        return this;
    }

    public AssignTaskCommandTestDataBuilder assigneeName(String assigneeName) {
        delegatedBuilder.assigneeName(assigneeName);
        return this;
    }

    public AssignTaskCommand build() {
        return delegatedBuilder.build();
    }
}
