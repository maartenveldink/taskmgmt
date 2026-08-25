package eu.poc.taskmanagement.saga;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic {@link DeadlineScheduler} for tests.
 *
 * <p>Instead of scheduling call-backs on real time, it records every scheduled
 * task and lets the test fire the most recently scheduled (still active) task on
 * demand via {@link #fireNext()}.  Cancellation is tracked so tests can assert a
 * process manager cleaned up.
 *
 * <h2>Two usage modes</h2>
 * <ul>
 *   <li><b>Plain unit tests</b> construct it directly ({@code new FakeDeadlineScheduler()})
 *       and pass it to a process-manager constructor.</li>
 *   <li><b>{@code @QuarkusTest}s</b> that need the real container (e.g. because the
 *       process manager persists state) enable it as a CDI {@link Alternative} via a
 *       test profile, replacing {@code ExecutorDeadlineScheduler}, and inject it to
 *       drive polling deterministically.</li>
 * </ul>
 */
@Alternative
@ApplicationScoped
@Unremovable
public class FakeDeadlineScheduler implements DeadlineScheduler {

    private record Scheduled(String id, Instant when, Runnable task) {
    }

    private final List<Scheduled> active = new ArrayList<>();
    private final AtomicInteger idSeq = new AtomicInteger();

    public int cancelCount = 0;

    @Override
    public String schedule(Instant when, Runnable task) {
        String id = "schedule-" + idSeq.incrementAndGet();
        active.add(new Scheduled(id, when, task));
        return id;
    }

    @Override
    public void cancel(String scheduleId) {
        cancelCount++;
        active.removeIf(s -> s.id().equals(scheduleId));
    }

    public boolean hasPending() {
        return !active.isEmpty();
    }

    public int pendingCount() {
        return active.size();
    }

    /** Clears all recorded schedules and counters — call between shared-instance tests. */
    public void reset() {
        active.clear();
        idSeq.set(0);
        cancelCount = 0;
    }

    /**
     * Fires (and removes) the most recently scheduled still-active task,
     * simulating its timer elapsing.
     */
    public void fireNext() {
        if (active.isEmpty()) {
            throw new IllegalStateException("No scheduled task to fire");
        }
        Scheduled next = active.remove(active.size() - 1);
        next.task().run();
    }
}
