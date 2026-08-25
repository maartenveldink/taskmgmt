package eu.poc.taskmanagement.saga;

import eu.poc.taskmanagement.model.TaskStatus;
import eu.poc.taskmanagement.model.TaskType;
import eu.poc.taskmanagement.model.command.MarkDeadlineExceededCommand;
import eu.poc.taskmanagement.model.event.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TaskDeadlineProcessManager}, the Axon-5 replacement for
 * the former {@code TaskDeadlineSaga}.
 *
 * <p>The process manager is a plain CDI bean, so it is tested directly with a
 * deterministic {@link FakeDeadlineScheduler} (fires the timer on demand) and a
 * mocked {@link CommandGateway}.
 *
 * <p>Coverage:
 * <ul>
 *   <li>A deadline is scheduled on {@code TaskCreatedEvent}</li>
 *   <li>No escalation when the task completes / is cancelled / rejected before the deadline</li>
 *   <li>Deadline exceeded: a {@link MarkDeadlineExceededCommand} is dispatched (DM-08)</li>
 *   <li>The command carries the original deadline instant</li>
 * </ul>
 */
class TaskDeadlineProcessManagerTest {

    private static final String TASK_ID = "pm-test-task";

    private FakeDeadlineScheduler scheduler;
    private CommandGateway commandGateway;
    private TaskDeadlineProcessManager processManager;

    @BeforeEach
    void setup() {
        scheduler = new FakeDeadlineScheduler();
        commandGateway = Mockito.mock(CommandGateway.class);
        processManager = new TaskDeadlineProcessManager(scheduler, commandGateway, Runnable::run);
    }

    private Instant deadline() {
        return Instant.now().plus(1, ChronoUnit.HOURS);
    }

    private TaskCreatedEvent createdEvent(Instant deadline) {
        return new TaskCreatedEvent(TASK_ID, "Test Task", "desc",
                "unassigned", null, deadline, TaskType.STANDARD, List.of());
    }

    @Test
    @DisplayName("Schedules a deadline on TaskCreatedEvent")
    void schedulesDeadlineOnCreation() {
        processManager.on(createdEvent(deadline()));

        assertTrue(scheduler.hasPending());
        assertEquals(1, scheduler.pendingCount());
    }

    @Test
    @DisplayName("No escalation when task completes before deadline")
    void noEscalationWhenCompletedOnTime() {
        processManager.on(createdEvent(deadline()));
        processManager.on(new TaskAssignedEvent(TASK_ID, "unassigned", "alice", "unassigned", null));
        processManager.on(new TaskStartedEvent(TASK_ID, TaskStatus.ASSIGNED));
        processManager.on(new TaskCompletedEvent(TASK_ID, TaskStatus.IN_PROGRESS));

        assertFalse(scheduler.hasPending());
        Mockito.verifyNoInteractions(commandGateway);
    }

    @Test
    @DisplayName("No escalation when task is cancelled")
    void noEscalationWhenCancelled() {
        processManager.on(createdEvent(deadline()));
        processManager.on(new TaskCancelledEvent(TASK_ID, TaskStatus.CREATED, "not needed"));

        assertFalse(scheduler.hasPending());
        Mockito.verifyNoInteractions(commandGateway);
    }

    @Test
    @DisplayName("No escalation when task is rejected")
    void noEscalationWhenRejected() {
        processManager.on(createdEvent(deadline()));
        processManager.on(new TaskRejectedEvent(TASK_ID, TaskStatus.CREATED, "invalid"));

        assertFalse(scheduler.hasPending());
        Mockito.verifyNoInteractions(commandGateway);
    }

    @Test
    @DisplayName("Deadline exceeded — dispatches MarkDeadlineExceededCommand with the original deadline")
    void escalatesWhenDeadlineExceeded() {
        Instant deadline = deadline();
        processManager.on(createdEvent(deadline));

        scheduler.fireNext();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(commandGateway).sendAndWait(captor.capture());

        Object command = captor.getValue();
        assertTrue(command instanceof MarkDeadlineExceededCommand);
        MarkDeadlineExceededCommand mark = (MarkDeadlineExceededCommand) command;
        assertEquals(TASK_ID, mark.taskId());
        assertEquals(deadline, mark.deadline());
    }

    @Test
    @DisplayName("Deadline fires after task already terminal — no command dispatched")
    void noEscalationWhenAlreadyTerminalAtFireTime() {
        Instant deadline = deadline();
        processManager.on(createdEvent(deadline));
        // Terminal event cancels the schedule; firing anyway must be a no-op.
        processManager.on(new TaskCompletedEvent(TASK_ID, TaskStatus.IN_PROGRESS));

        assertFalse(scheduler.hasPending());
        Mockito.verifyNoInteractions(commandGateway);
    }
}
