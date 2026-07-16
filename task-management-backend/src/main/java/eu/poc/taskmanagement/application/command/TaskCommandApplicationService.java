package eu.poc.taskmanagement.application.command;

import eu.poc.taskmanagement.model.command.AssignTaskCommand;
import eu.poc.taskmanagement.model.command.CancelTaskCommand;
import eu.poc.taskmanagement.model.command.CompleteTaskCommand;
import eu.poc.taskmanagement.model.command.CreateTaskCommand;
import eu.poc.taskmanagement.model.command.ReassignTaskCommand;
import eu.poc.taskmanagement.model.command.RejectTaskCommand;
import eu.poc.taskmanagement.model.command.StartTaskCommand;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;

@ApplicationScoped
public class TaskCommandApplicationService {

    @Inject
    CommandGateway commandGateway;

    public void handle(CreateTaskCommand command) {
        if (command.taskType() == eu.poc.taskmanagement.model.TaskType.USER_PROVISIONING
                && (command.expectedExternalUsers() == null || command.expectedExternalUsers().isEmpty())) {
            throw new IllegalArgumentException(
                    "expectedExternalUsers must contain at least one user for taskType USER_PROVISIONING");
        }
        dispatch(command);
    }

    public void handle(AssignTaskCommand command) {
        dispatch(command);
    }

    public void handle(ReassignTaskCommand command) {
        dispatch(command);
    }

    public void handle(StartTaskCommand command) {
        dispatch(command);
    }

    public void handle(CompleteTaskCommand command) {
        dispatch(command);
    }

    public void handle(CancelTaskCommand command) {
        dispatch(command);
    }

    public void handle(RejectTaskCommand command) {
        dispatch(command);
    }

    private void dispatch(Object command) {
        try {
            commandGateway.sendAndWait(command);
        } catch (CommandExecutionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause);
        }
    }

}
