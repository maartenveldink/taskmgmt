package eu.poc.taskmanagement.model;

import eu.poc.taskmanagement.model.command.*;
import eu.poc.taskmanagement.model.event.*;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

class TaskAggregateTest {

    private FixtureConfiguration<TaskAggregate> fixture;

    private static final String TASK_ID = "test-correlation-id";
    private static final Instant DEADLINE = Instant.now().plus(1, ChronoUnit.DAYS);

    @BeforeEach
    void setup() {
        fixture = new AggregateTestFixture<>(TaskAggregate.class);
    }

    private static TaskCreatedEvent createdEvent() {
        return new TaskCreatedEvent(
                TASK_ID, "T", "D", "grp", null, DEADLINE, TaskType.STANDARD, List.of());
    }

    @Nested
    @DisplayName("CreateTaskCommand")
    class Create {

        @Test
        void createsTask() {
            fixture.givenNoPriorActivity()
                    .when(new CreateTaskCommand(
                            TASK_ID, "My Task", "Description", "team-alpha", DEADLINE,
                            TaskType.STANDARD, List.of()))
                    .expectSuccessfulHandlerExecution()
                    .expectEvents(new TaskCreatedEvent(
                            TASK_ID, "My Task", "Description", "team-alpha", null, DEADLINE,
                            TaskType.STANDARD, List.of()));
        }
    }

    @Nested
    @DisplayName("AssignTaskCommand")
    class Assign {

        @Test
        void assignsToUser() {
            fixture.given(new TaskCreatedEvent(
                            TASK_ID, "T", "D", "unassigned", null, DEADLINE, TaskType.STANDARD, List.of()))
                    .when(new AssignTaskCommand(TASK_ID, "alice", AssigneeType.USER))
                    .expectSuccessfulHandlerExecution()
                    .expectEvents(new TaskAssignedEvent(
                            TASK_ID, "unassigned", "alice", "unassigned", null));
        }

        @Test
        void rejectsAssignOnDone() {
            fixture.given(
                            createdEvent(),
                            new TaskAssignedEvent(TASK_ID, "grp", "alice", "grp", null),
                            new TaskStartedEvent(TASK_ID, TaskStatus.ASSIGNED),
                            new TaskCompletedEvent(TASK_ID, TaskStatus.IN_PROGRESS))
                    .when(new AssignTaskCommand(TASK_ID, "bob", AssigneeType.USER))
                    .expectException(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("ReassignTaskCommand")
    class Reassign {

        @Test
        void reassignsGroupToUser() {
            fixture.given(
                            new TaskCreatedEvent(
                                    TASK_ID, "T", "D", "team-beta", null, DEADLINE, TaskType.STANDARD, List.of()),
                            new TaskAssignedEvent(TASK_ID, "team-beta", null, "team-beta", null))
                    .when(new ReassignTaskCommand(TASK_ID, "charlie", AssigneeType.USER))
                    .expectSuccessfulHandlerExecution()
                    .expectEvents(new TaskReassignedEvent(
                            TASK_ID, "team-beta", null, "team-beta", "charlie"));
        }
    }

    @Nested
    @DisplayName("StartTaskCommand")
    class Start {

        @Test
        void startsTask() {
            fixture.given(
                            createdEvent(),
                            new TaskAssignedEvent(TASK_ID, "grp", "alice", "grp", null))
                    .when(new StartTaskCommand(TASK_ID))
                    .expectSuccessfulHandlerExecution()
                    .expectEvents(new TaskStartedEvent(TASK_ID, TaskStatus.ASSIGNED));
        }

        @Test
        void rejectsStartOnCreated() {
            fixture.given(createdEvent())
                    .when(new StartTaskCommand(TASK_ID))
                    .expectException(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("CompleteTaskCommand")
    class Complete {

        @Test
        void completesTask() {
            fixture.given(
                            createdEvent(),
                            new TaskAssignedEvent(TASK_ID, "grp", "alice", "grp", null),
                            new TaskStartedEvent(TASK_ID, TaskStatus.ASSIGNED))
                    .when(new CompleteTaskCommand(TASK_ID))
                    .expectSuccessfulHandlerExecution()
                    .expectEvents(new TaskCompletedEvent(TASK_ID, TaskStatus.IN_PROGRESS));
        }

        @Test
        void rejectsCompleteOnAssigned() {
            fixture.given(
                            createdEvent(),
                            new TaskAssignedEvent(TASK_ID, "grp", "alice", "grp", null))
                    .when(new CompleteTaskCommand(TASK_ID))
                    .expectException(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("CancelTaskCommand")
    class Cancel {

        @Test
        void cancelsFromAssigned() {
            fixture.given(
                            createdEvent(),
                            new TaskAssignedEvent(TASK_ID, "grp", "alice", "grp", null))
                    .when(new CancelTaskCommand(TASK_ID, "no longer needed"))
                    .expectSuccessfulHandlerExecution()
                    .expectEvents(new TaskCancelledEvent(TASK_ID, TaskStatus.ASSIGNED, "no longer needed"));
        }

        @Test
        void rejectsCancelOnCancelled() {
            fixture.given(
                            createdEvent(),
                            new TaskCancelledEvent(TASK_ID, TaskStatus.CREATED, "first cancel"))
                    .when(new CancelTaskCommand(TASK_ID, "second cancel"))
                    .expectException(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("RejectTaskCommand")
    class Reject {

        @Test
        void rejectsFromCreated() {
            fixture.given(createdEvent())
                    .when(new RejectTaskCommand(TASK_ID, "invalid request"))
                    .expectSuccessfulHandlerExecution()
                    .expectEvents(new TaskRejectedEvent(TASK_ID, TaskStatus.CREATED, "invalid request"));
        }

        @Test
        void rejectsRejectOnInProgress() {
            fixture.given(
                            createdEvent(),
                            new TaskAssignedEvent(TASK_ID, "grp", "alice", "grp", null),
                            new TaskStartedEvent(TASK_ID, TaskStatus.ASSIGNED))
                    .when(new RejectTaskCommand(TASK_ID, "too late"))
                    .expectException(IllegalStateException.class);
        }
    }
}
