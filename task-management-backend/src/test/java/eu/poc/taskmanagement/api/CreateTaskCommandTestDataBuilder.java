package eu.poc.taskmanagement.api;

import eu.poc.taskmanagement.model.command.CreateTaskCommand;
import eu.poc.taskmanagement.model.command.CreateTaskCommand.CreateTaskCommandBuilder;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

import java.time.Instant;

@RequiredArgsConstructor(staticName = "from")
public class CreateTaskCommandTestDataBuilder {

    @Delegate
    private final CreateTaskCommandBuilder delegatedBuilder;

    /**
     * Create a functionally valid Entity, consistent with other testdatabuilders.
     * This method defines the values for a valid object, that can be successfully handled by the application.
     *
     * @return CreateTaskCommandTestDataBuilder
     */
    public static CreateTaskCommandTestDataBuilder valid(){
        CreateTaskCommandBuilder builder = CreateTaskCommand.builder()
                .taskId("task-" + System.nanoTime())
                .title("Sample Task")
                .description("This is a sample task for testing.")
                .groupName("test-group")
                .deadline(Instant.now().plusSeconds(3600));

        return from(builder);
    }


}
