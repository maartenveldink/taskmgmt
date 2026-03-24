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

/**
 * Unit tests for {@link TaskAggregate} using Axon's {@link AggregateTestFixture}.
 *
 * <p>The fixture replaces the full Axon infrastructure with an in-memory
 * equivalent, so no Quarkus context is needed.  Tests follow the
 * Given-When-Then (GWT) style mandated by the Axon test API.
 *
 * <p>Coverage:
 * <ul>
 *   <li>All happy-path state transitions</li>
 *   <li>Terminal-state guards (no command accepted after Done/Cancelled/Rejected)</li>
 *   <li>Invalid state transition preconditions</li>
 * </ul>
 */
class TaskAggregateTest {

    private FixtureConfiguration<TaskAggregate> fixture;

    private static final String TASK_ID = "test-correlation-id";
    private static final Instant DEADLINE = Instant.now().plus(1, ChronoUnit.DAYS);

    @BeforeEach
    void setup() {
        fixture = new AggregateTestFixture<>(TaskAggregate.class);
    }

    // =========================================================================
    // Create
    // =========================================================================

    @Nested
    @DisplayName("CreateTaskCommand")
    class Create {

        @Test
        @DisplayName("publishes TaskCreatedEvent with GROUP assignee type")
        void createsTask() {
            fixture.givenNoPriorActivity()
                    .when(new CreateTaskCommand(TASK_ID, "My Task", "Description",
                            "team-alpha", DEADLINE))
                    .expectSuccessfulHandlerExecution()
                    .expectEvents(new TaskCreatedEvent(
                            TASK_ID, "My Task", "Description",
                            "team-alpha", null, DEADLINE));
        }
    }

    // =========================================================================
    // Assign
    // =========================================================================

    @Nested
    @DisplayName("AssignTaskCommand")
    class Assign {

        @Test
        @DisplayName("assigns to a user — publishes TaskAssignedEvent")
        void assignsToUser() {
            fixture.given(new TaskCreatedEvent(TASK_ID, "T", "D",
                            "unassigned", null, DEADLINE))
                    .when(new AssignTaskCommand(TASK_ID, "alice", AssigneeType.USER))
                    .expectSuccessfulHandlerExecution()
                    .expectEvents(new TaskAssignedEvent(
                            TASK_ID, "unassigned", "alice", "unassigned", null));
        }

        @Test
        @DisplayName("rejected when task is DONE")
        void rejectsAssignOnDone() {
            fixture.given(
                            new TaskCreatedEvent(TASK_ID, "T", "D", "grp", null, DEADLINE),
                            new TaskAssignedEvent(TASK_ID, "grp", "alice", "grp", null),
                            new TaskStartedEvent(TASK_ID, TaskStatus.ASSIGNED),
                            new TaskCompletedEvent(TASK_ID, TaskStatus.IN_PROGRESS))
                    .when(new AssignTaskCommand(TASK_ID, "bob", AssigneeType.USER))
                    .expectException(IllegalStateException.class);
        }
    }

    // =========================================================================
    // Reassign
    // =========================================================================

    @Nested
    @DisplayName("ReassignTaskCommand")
    class Reassign {

        @Test
        @DisplayName("reassigns from group to user — publishes TaskReassignedEvent")
        void reassignsGroupToUser() {
            fixture.given(
                            new TaskCreatedEvent(TASK_ID, "T", "D", "team-beta", null, DEADLINE),
                            new TaskAssignedEvent(TASK_ID, "team-beta", null, "team-beta", null))
                    .when(new ReassignTaskCommand(TASK_ID, "charlie", AssigneeType.USER))
                    .expectSuccessfulHandlerExecution()
                    .expectEvents(new TaskReassignedEvent(
                            TASK_ID, "team-beta", null, "team-beta", "charlie"));
        }
    }

    // =========================================================================
    // Start
    // =========================================================================

    @Nested
    @DisplayName("StartTaskCommand")
    class Start {

        @Test
        @DisplayName("happy path ASSIGNED → IN_PROGRESS")
        void startsTask() {
            fixture.given(
                            new TaskCreatedEvent(TASK_ID, "T", "D", "grp", null, DEADLINE),
                            new TaskAssignedEvent(TASK_ID, "grp", "alice", "grp", null))
                    .when(new StartTaskCommand(TASK_ID))
                    .expectSuccessfulHandlerExecution()
                    .expectEvents(new TaskStartedEvent(TASK_ID, TaskStatus.ASSIGNED));
        }

        @Test
        @DisplayName("rejected when task is CREATED (not yet assigned)")
        void rejectsStartOnCreated() {
            fixture.given(new TaskCreatedEvent(TASK_ID, "T", "D", "grp", null, DEADLINE))
                    .when(new StartTaskCommand(TASK_ID))
                    .expectException(IllegalStateException.class);
        }
    }

    // =========================================================================
    // Complete
    // =========================================================================

    @Nested
    @DisplayName("CompleteTaskCommand")
    class Complete {

        @Test
        @DisplayName("happy path IN_PROGRESS → DONE")
        void completesTask() {
            fixture.given(
                            new TaskCreatedEvent(TASK_ID, "T", "D", "grp", null, DEADLINE),
                            new TaskAssignedEvent(TASK_ID, "grp", "alice", "grp", null),
                            new TaskStartedEvent(TASK_ID, TaskStatus.ASSIGNED))
                    .when(new CompleteTaskCommand(TASK_ID))
                    .expectSuccessfulHandlerExecution()
                    .expectEvents(new TaskCompletedEvent(TASK_ID, TaskStatus.IN_PROGRESS));
        }

        @Test
        @DisplayName("rejected when task is ASSIGNED (not IN_PROGRESS)")
        void rejectsCompleteOnAssigned() {
            fixture.given(
                            new TaskCreatedEvent(TASK_ID, "T", "D", "grp", null, DEADLINE),
                            new TaskAssignedEvent(TASK_ID, "grp", "alice", "grp", null))
                    .when(new CompleteTaskCommand(TASK_ID))
                    .expectException(IllegalStateException.class);
        }
    }

    // =========================================================================
    // Cancel
    // =========================================================================

    @Nested
    @DisplayName("CancelTaskCommand")
    class Cancel {

        @Test
        @DisplayName("cancels from ASSIGNED — publishes TaskCancelledEvent with reason")
        void cancelsFromAssigned() {
            fixture.given(
                            new TaskCreatedEvent(TASK_ID, "T", "D", "grp", null, DEADLINE),
                            new TaskAssignedEvent(TASK_ID, "grp", "alice", "grp", null))
                    .when(new CancelTaskCommand(TASK_ID, "no longer needed"))
                    .expectSuccessfulHandlerExecution()
                    .expectEvents(new TaskCancelledEvent(TASK_ID, TaskStatus.ASSIGNED, "no longer needed"));
        }

        @Test
        @DisplayName("rejected when task is already CANCELLED")
        void rejectsCancelOnCancelled() {
            fixture.given(
                            new TaskCreatedEvent(TASK_ID, "T", "D", "grp", null, DEADLINE),
                            new TaskCancelledEvent(TASK_ID, TaskStatus.CREATED, "first cancel"))
                    .when(new CancelTaskCommand(TASK_ID, "second cancel"))
                    .expectException(IllegalStateException.class);
        }
    }

    // =========================================================================
    // Reject
    // =========================================================================

    @Nested
    @DisplayName("RejectTaskCommand")
    class Reject {

        @Test
        @DisplayName("rejects from CREATED — publishes TaskRejectedEvent")
        void rejectsFromCreated() {
            fixture.given(new TaskCreatedEvent(TASK_ID, "T", "D", "grp", null, DEADLINE))
                    .when(new RejectTaskCommand(TASK_ID, "invalid request"))
                    .expectSuccessfulHandlerExecution()
                    .expectEvents(new TaskRejectedEvent(TASK_ID, TaskStatus.CREATED, "invalid request"));
        }

        @Test
        @DisplayName("rejected when task is IN_PROGRESS (past ASSIGNED)")
        void rejectsRejectOnInProgress() {
            fixture.given(
                            new TaskCreatedEvent(TASK_ID, "T", "D", "grp", null, DEADLINE),
                            new TaskAssignedEvent(TASK_ID, "grp", "alice", "grp", null),
                            new TaskStartedEvent(TASK_ID, TaskStatus.ASSIGNED))
                    .when(new RejectTaskCommand(TASK_ID, "too late"))
                    .expectException(IllegalStateException.class);
        }
    }
}
