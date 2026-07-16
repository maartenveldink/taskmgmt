package eu.poc.taskmanagement.saga;

import eu.poc.taskmanagement.model.TaskStatus;
import eu.poc.taskmanagement.model.TaskType;
import eu.poc.taskmanagement.model.event.*;
import org.axonframework.test.saga.FixtureConfiguration;
import org.axonframework.test.saga.SagaTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Unit tests for {@link TaskDeadlineSaga} using Axon's {@link SagaTestFixture}.
 *
 * <p>The saga fixture provides virtual time control, so deadline tests do not
 * need to sleep — they call {@code whenTimeElapses(Duration)} instead.
 *
 * <h2>API reference (Axon 4.9)</h2>
 * <ul>
 *   <li>{@code givenNoPriorActivity().whenPublishingA(event)} — publishes event, saga not yet started</li>
 *   <li>{@code givenAPublished(event)} — saga is started by this event</li>
 *   <li>{@code .andThenAPublished(event)} — additional prior events</li>
 *   <li>{@code .whenPublishingA(event)} — the event under test</li>
 *   <li>{@code .whenTimeElapses(duration)} — advance virtual time (fires deadlines)</li>
 * </ul>
 *
 * <p>Coverage:
 * <ul>
 *   <li>Saga starts and schedules a deadline on {@code TaskCreatedEvent}</li>
 *   <li>No escalation when task completes / is cancelled / rejected before deadline</li>
 *   <li>Deadline exceeded: {@code TaskDeadlineExceededEvent} published (DM-08)</li>
 *   <li>Status is tracked correctly by the saga</li>
 * </ul>
 */
class TaskDeadlineSagaTest {

    private FixtureConfiguration fixture;

    private static final String TASK_ID = "saga-test-task";

    @BeforeEach
    void setup() {
        fixture = new SagaTestFixture<>(TaskDeadlineSaga.class);
    }

    /**
     * Deadline 1 hour from the fixture's virtual "now".
     * Must be computed inside each test after the fixture initialises its clock.
     */
    private Instant deadlineFromNow() {
        return fixture.currentTime().plus(Duration.ofHours(1));
    }

    private TaskCreatedEvent createdEvent() {
        return new TaskCreatedEvent(TASK_ID, "Test Task", "desc",
                "unassigned", null, deadlineFromNow(), TaskType.STANDARD, List.of());
    }

    // =========================================================================
    // Saga start
    // =========================================================================

    @Test
    @DisplayName("Saga starts on TaskCreatedEvent and schedules a deadline at the event's deadline instant")
    void sagaStartsOnCreation() {
        Instant deadline = deadlineFromNow();
        TaskCreatedEvent created = new TaskCreatedEvent(TASK_ID, "Test", "desc",
                "unassigned", null, deadline, TaskType.STANDARD, List.of());

        fixture.givenNoPriorActivity()
                .whenPublishingA(created)
                .expectActiveSagas(1)
                // Assert the deadline was scheduled at the exact Instant from the event.
                .expectScheduledDeadlineWithName(deadline, TaskDeadlineSaga.DEADLINE_NAME);
    }

    // =========================================================================
    // Terminal events cancel the deadline — no escalation
    // =========================================================================

    @Test
    @DisplayName("Saga ends cleanly when task is completed before deadline")
    void noEscalationWhenCompletedOnTime() {
        fixture.givenAPublished(createdEvent())
                .andThenAPublished(new TaskAssignedEvent(TASK_ID, "unassigned", "alice", "unassigned", null))
                .andThenAPublished(new TaskStartedEvent(TASK_ID, TaskStatus.ASSIGNED))
                .whenPublishingA(new TaskCompletedEvent(TASK_ID, TaskStatus.IN_PROGRESS))
                .expectActiveSagas(0)
                .expectNoScheduledDeadlines();
    }

    @Test
    @DisplayName("Saga ends cleanly when task is cancelled — no escalation")
    void noEscalationWhenCancelled() {
        fixture.givenAPublished(createdEvent())
                .whenPublishingA(new TaskCancelledEvent(TASK_ID, TaskStatus.CREATED, "not needed"))
                .expectActiveSagas(0)
                .expectNoScheduledDeadlines();
    }

    @Test
    @DisplayName("Saga ends cleanly when task is rejected — no escalation")
    void noEscalationWhenRejected() {
        fixture.givenAPublished(createdEvent())
                .whenPublishingA(new TaskRejectedEvent(TASK_ID, TaskStatus.CREATED, "invalid"))
                .expectActiveSagas(0)
                .expectNoScheduledDeadlines();
    }

    // =========================================================================
    // Deadline exceeded (requirement DM-08 — short deadline / time elapsed)
    // =========================================================================

    @Test
    @DisplayName("Deadline fires when time elapses — TaskDeadlineExceededEvent published")
    void escalatesWhenDeadlineExceeded() {
        Instant deadline = deadlineFromNow();
        TaskCreatedEvent created = new TaskCreatedEvent(TASK_ID, "Test", "desc",
                "unassigned", null, deadline, TaskType.STANDARD, List.of());

        fixture.givenAPublished(created)
                // Advance virtual time 2 hours past the 1-hour deadline.
                .whenTimeElapses(Duration.ofHours(2))
                .expectActiveSagas(0)
                // Saga publishes this event via EventBus for external subscribers.
                .expectPublishedEvents(
                        new TaskDeadlineExceededEvent(TASK_ID, deadline, TaskStatus.CREATED));
    }

    @Test
    @DisplayName("Saga tracks status — deadline event contains last known status (IN_PROGRESS)")
    void includesLatestStatusInDeadlineEvent() {
        Instant deadline = deadlineFromNow();
        TaskCreatedEvent created = new TaskCreatedEvent(TASK_ID, "Test", "desc",
                "unassigned", null, deadline, TaskType.STANDARD, List.of());

        fixture.givenAPublished(created)
                .andThenAPublished(new TaskAssignedEvent(TASK_ID, "team-alpha", null, "unassigned", null))
                .andThenAPublished(new TaskStartedEvent(TASK_ID, TaskStatus.ASSIGNED))
                .whenTimeElapses(Duration.ofHours(2))
                .expectActiveSagas(0)
                .expectPublishedEvents(
                        new TaskDeadlineExceededEvent(TASK_ID, deadline, TaskStatus.IN_PROGRESS));
    }
}
