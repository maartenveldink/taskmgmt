package eu.poc.taskmanagement.saga;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic {@link DeadlineScheduler} for unit tests.
 *
 * <p>Instead of scheduling call-backs on real time, it records every scheduled
 * task and lets the test fire the most recently scheduled (still active) task on
 * demand via {@link #fireNext()}.  Cancellation is tracked so tests can assert a
 * process manager cleaned up.
 */
class FakeDeadlineScheduler implements DeadlineScheduler {

    private record Scheduled(String id, Instant when, Runnable task) {
    }

    private final List<Scheduled> active = new ArrayList<>();
    private final AtomicInteger idSeq = new AtomicInteger();

    int cancelCount = 0;

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

    boolean hasPending() {
        return !active.isEmpty();
    }

    int pendingCount() {
        return active.size();
    }

    /**
     * Fires (and removes) the most recently scheduled still-active task,
     * simulating its timer elapsing.
     */
    void fireNext() {
        if (active.isEmpty()) {
            throw new IllegalStateException("No scheduled task to fire");
        }
        Scheduled next = active.remove(active.size() - 1);
        next.task().run();
    }
}
