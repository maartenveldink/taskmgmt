package eu.poc.taskmanagement.testdata;

import eu.poc.taskmanagement.model.command.CompleteTaskCommand;
import eu.poc.taskmanagement.model.command.CompleteTaskCommand.CompleteTaskCommandBuilder;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

/**
 * Test data builder for CompleteTaskCommand.
 * Delegates to Lombok's generated builder, adding factory methods
 * for common test scenarios.
 */
@RequiredArgsConstructor(staticName = "from")
public class CompleteTaskCommandTestDataBuilder {

    @Delegate
    private final CompleteTaskCommandBuilder delegatedBuilder;

    /**
     * Create a functionally valid command with sensible test defaults.
     *
     * @return CompleteTaskCommandTestDataBuilder
     */
    public static CompleteTaskCommandTestDataBuilder valid() {
        CompleteTaskCommandBuilder builder = CompleteTaskCommand.builder()
                .taskId("task-" + System.nanoTime());

        return new CompleteTaskCommandTestDataBuilder(builder);
    }

    public CompleteTaskCommandTestDataBuilder taskId(String taskId) {
        delegatedBuilder.taskId(taskId);
        return this;
    }

    public CompleteTaskCommand build() {
        return delegatedBuilder.build();
    }
}
