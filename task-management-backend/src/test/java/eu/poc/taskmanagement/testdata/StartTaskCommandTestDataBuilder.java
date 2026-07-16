package eu.poc.taskmanagement.testdata;

import eu.poc.taskmanagement.model.command.StartTaskCommand;
import eu.poc.taskmanagement.model.command.StartTaskCommand.StartTaskCommandBuilder;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

/**
 * Test data builder for StartTaskCommand.
 * Delegates to Lombok's generated builder, adding factory methods
 * for common test scenarios.
 */
@RequiredArgsConstructor(staticName = "from")
public class StartTaskCommandTestDataBuilder {

    @Delegate
    private final StartTaskCommandBuilder delegatedBuilder;

    /**
     * Create a functionally valid command with sensible test defaults.
     *
     * @return StartTaskCommandTestDataBuilder
     */
    public static StartTaskCommandTestDataBuilder valid() {
        StartTaskCommandBuilder builder = StartTaskCommand.builder()
                .taskId("task-" + System.nanoTime());

        return new StartTaskCommandTestDataBuilder(builder);
    }

    public StartTaskCommandTestDataBuilder taskId(String taskId) {
        delegatedBuilder.taskId(taskId);
        return this;
    }

    public StartTaskCommand build() {
        return delegatedBuilder.build();
    }
}
