package eu.poc.taskmanagement.model;

import eu.poc.taskmanagement.model.command.*;
import eu.poc.taskmanagement.model.event.*;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

class TaskAggregateTest {

    private AxonTestFixture fixture;

    private static final String TASK_ID = "test-correlation-id";
    private static final Instant DEADLINE = Instant.now().plus(1, ChronoUnit.DAYS);

    @BeforeEach
    void setup() {
        fixture = AxonTestFixture.with(
                EventSourcingConfigurer.create()
                        .registerEntity(EventSourcedEntityModule.autodetected(String.class, TaskAggregate.class)));
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
            fixture.given().noPriorActivity()
                    .when().command(new CreateTaskCommand(
                            TASK_ID, "My Task", "Description", "team-alpha", DEADLINE,
                            TaskType.STANDARD, List.of()))
                    .then().success()
                    .events(new TaskCreatedEvent(
                            TASK_ID, "My Task", "Description", "team-alpha", null, DEADLINE,
                            TaskType.STANDARD, List.of()));
        }
    }

    @Nested
    @DisplayName("AssignTaskCommand")
    class Assign {

        @Test
        void assignsToUser() {
            fixture.given().events(new TaskCreatedEvent(
                            TASK_ID, "T", "D", "unassigned", null, DEADLINE, TaskType.STANDARD, List.of()))
                    .when().command(new AssignTaskCommand(TASK_ID, "alice", AssigneeType.USER))
                    .then().success()
                    .events(new TaskAssignedEvent(
                            TASK_ID, "unassigned", "alice", "unassigned", null));
        }

        @Test
        void rejectsAssignOnDone() {
            fixture.given().events(
                            createdEvent(),
                            new TaskAssignedEvent(TASK_ID, "grp", "alice", "grp", null),
                            new TaskStartedEvent(TASK_ID, TaskStatus.ASSIGNED),
                            new TaskCompletedEvent(TASK_ID, TaskStatus.IN_PROGRESS))
                    .when().command(new AssignTaskCommand(TASK_ID, "bob", AssigneeType.USER))
                    .then().exception(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("ReassignTaskCommand")
    class Reassign {

        @Test
        void reassignsGroupToUser() {
            fixture.given().events(
                            new TaskCreatedEvent(
                                    TASK_ID, "T", "D", "team-beta", null, DEADLINE, TaskType.STANDARD, List.of()),
                            new TaskAssignedEvent(TASK_ID, "team-beta", null, "team-beta", null))
                    .when().command(new ReassignTaskCommand(TASK_ID, "charlie", AssigneeType.USER))
                    .then().success()
                    .events(new TaskReassignedEvent(
                            TASK_ID, "team-beta", null, "team-beta", "charlie"));
        }
    }

    @Nested
    @DisplayName("StartTaskCommand")
    class Start {

        @Test
        void startsTask() {
            fixture.given().events(
                            createdEvent(),
                            new TaskAssignedEvent(TASK_ID, "grp", "alice", "grp", null))
                    .when().command(new StartTaskCommand(TASK_ID))
                    .then().success()
                    .events(new TaskStartedEvent(TASK_ID, TaskStatus.ASSIGNED));
        }

        @Test
        void rejectsStartOnCreated() {
            fixture.given().events(createdEvent())
                    .when().command(new StartTaskCommand(TASK_ID))
                    .then().exception(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("CompleteTaskCommand")
    class Complete {

        @Test
        void completesTask() {
            fixture.given().events(
                            createdEvent(),
                            new TaskAssignedEvent(TASK_ID, "grp", "alice", "grp", null),
                            new TaskStartedEvent(TASK_ID, TaskStatus.ASSIGNED))
                    .when().command(new CompleteTaskCommand(TASK_ID))
                    .then().success()
                    .events(new TaskCompletedEvent(TASK_ID, TaskStatus.IN_PROGRESS));
        }

        @Test
        void rejectsCompleteOnAssigned() {
            fixture.given().events(
                            createdEvent(),
                            new TaskAssignedEvent(TASK_ID, "grp", "alice", "grp", null))
                    .when().command(new CompleteTaskCommand(TASK_ID))
                    .then().exception(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("CancelTaskCommand")
    class Cancel {

        @Test
        void cancelsFromAssigned() {
            fixture.given().events(
                            createdEvent(),
                            new TaskAssignedEvent(TASK_ID, "grp", "alice", "grp", null))
                    .when().command(new CancelTaskCommand(TASK_ID, "no longer needed"))
                    .then().success()
                    .events(new TaskCancelledEvent(TASK_ID, TaskStatus.ASSIGNED, "no longer needed"));
        }

        @Test
        void rejectsCancelOnCancelled() {
            fixture.given().events(
                            createdEvent(),
                            new TaskCancelledEvent(TASK_ID, TaskStatus.CREATED, "first cancel"))
                    .when().command(new CancelTaskCommand(TASK_ID, "second cancel"))
                    .then().exception(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("RejectTaskCommand")
    class Reject {

        @Test
        void rejectsFromCreated() {
            fixture.given().events(createdEvent())
                    .when().command(new RejectTaskCommand(TASK_ID, "invalid request"))
                    .then().success()
                    .events(new TaskRejectedEvent(TASK_ID, TaskStatus.CREATED, "invalid request"));
        }

        @Test
        void rejectsRejectOnInProgress() {
            fixture.given().events(
                            createdEvent(),
                            new TaskAssignedEvent(TASK_ID, "grp", "alice", "grp", null),
                            new TaskStartedEvent(TASK_ID, TaskStatus.ASSIGNED))
                    .when().command(new RejectTaskCommand(TASK_ID, "too late"))
                    .then().exception(IllegalStateException.class);
        }
    }
}
