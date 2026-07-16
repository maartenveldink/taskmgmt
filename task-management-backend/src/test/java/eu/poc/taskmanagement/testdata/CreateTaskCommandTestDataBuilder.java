package eu.poc.taskmanagement.testdata;

import eu.poc.taskmanagement.model.command.CreateTaskCommand;
import eu.poc.taskmanagement.model.command.CreateTaskCommand.CreateTaskCommandBuilder;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

import java.time.Instant;
import java.util.List;

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
                .deadline(Instant.now().plusSeconds(3600))
                .taskType(eu.poc.taskmanagement.model.TaskType.STANDARD)
                .expectedExternalUsers(List.of());

        return new CreateTaskCommandTestDataBuilder(builder);
    }

    public CreateTaskCommandTestDataBuilder taskId(String taskId) {
        delegatedBuilder.taskId(taskId);
        return this;
    }

    public CreateTaskCommandTestDataBuilder title(String title) {
        delegatedBuilder.title(title);
        return this;
    }

    public CreateTaskCommandTestDataBuilder description(String description) {
        delegatedBuilder.description(description);
        return this;
    }

    public CreateTaskCommandTestDataBuilder groupName(String groupName) {
        delegatedBuilder.groupName(groupName);
        return this;
    }

    public CreateTaskCommandTestDataBuilder deadline(Instant deadline) {
        delegatedBuilder.deadline(deadline);
        return this;
    }

    public CreateTaskCommand build() {
        return delegatedBuilder.build();
    }

}
