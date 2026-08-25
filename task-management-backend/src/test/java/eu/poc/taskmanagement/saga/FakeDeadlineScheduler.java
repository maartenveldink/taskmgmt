package eu.poc.taskmanagement.saga;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic {@link DeadlineScheduler} for tests.
 *
 * <p>Instead of scheduling call-backs on real time, it records every scheduled
 * job and lets the test fire the most recently scheduled (still active) job on
 * demand via {@link #fireNext()}, dispatching it to the matching
 * {@link ScheduledJobHandler} exactly as the production scheduler would.
 * Cancellation is tracked so tests can assert a process manager cleaned up.
 *
 * <h2>Two usage modes</h2>
 * <ul>
 *   <li><b>Plain unit tests</b> construct it directly ({@code new FakeDeadlineScheduler()}),
 *       pass it to a process-manager constructor and {@link #register(ScheduledJobHandler)}
 *       that manager. {@link #fireNext()} then runs the handler with no transaction
 *       wrapping (nothing touches the database).</li>
 *   <li><b>{@code @QuarkusTest}s</b> that need the real container (e.g. because the
 *       process manager persists state) enable it as a CDI {@link Alternative} via a
 *       test profile, replacing {@code PersistentDeadlineScheduler}. Handlers and a
 *       real {@link TransactionRunner} are then discovered from CDI, so a fired job
 *       runs inside a real transaction — mirroring production.</li>
 * </ul>
 */
@Alternative
@ApplicationScoped
@Unremovable
public class FakeDeadlineScheduler implements DeadlineScheduler {

    private record Scheduled(String id, ScheduledJobType type, String taskId, Instant when) {
    }

    private final List<Scheduled> active = new ArrayList<>();
    private final AtomicInteger idSeq = new AtomicInteger();
    private final Map<ScheduledJobType, ScheduledJobHandler> handlers = new EnumMap<>(ScheduledJobType.class);

    public int cancelCount = 0;

    /** Present only under CDI; null when the class is instantiated with {@code new}. */
    @Inject
    Instance<ScheduledJobHandler> cdiHandlers;

    /** Present only under CDI; null when the class is instantiated with {@code new}. */
    @Inject
    Instance<TransactionRunner> cdiTransactionRunner;

    private TransactionRunner transactionRunner = Runnable::run;
    private boolean cdiResolved = false;

    /** Registers a handler for plain (non-CDI) unit tests. */
    public void register(ScheduledJobHandler handler) {
        handlers.put(handler.type(), handler);
    }

    private void resolveCdi() {
        if (cdiResolved) {
            return;
        }
        cdiResolved = true;
        if (cdiHandlers != null) {
            for (ScheduledJobHandler handler : cdiHandlers) {
                handlers.putIfAbsent(handler.type(), handler);
            }
        }
        if (cdiTransactionRunner != null && cdiTransactionRunner.isResolvable()) {
            transactionRunner = cdiTransactionRunner.get();
        }
    }

    @Override
    public String schedule(Instant when, ScheduledJobType type, String taskId) {
        String id = "schedule-" + idSeq.incrementAndGet();
        active.add(new Scheduled(id, type, taskId, when));
        return id;
    }

    @Override
    public void cancel(String scheduleId) {
        cancelCount++;
        active.removeIf(s -> s.id().equals(scheduleId));
    }

    @Override
    public void cancelAll(ScheduledJobType type, String taskId) {
        cancelCount++;
        active.removeIf(s -> s.type() == type && s.taskId().equals(taskId));
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
     * Fires (and removes) the most recently scheduled still-active job, simulating
     * its timer elapsing, dispatching it to the registered handler inside a
     * transaction (real under CDI, direct otherwise).
     */
    public void fireNext() {
        resolveCdi();
        if (active.isEmpty()) {
            throw new IllegalStateException("No scheduled task to fire");
        }
        Scheduled next = active.remove(active.size() - 1);
        ScheduledJobHandler handler = handlers.get(next.type());
        if (handler == null) {
            throw new IllegalStateException("No handler registered for scheduled job type " + next.type());
        }
        transactionRunner.runInTransaction(() -> handler.execute(next.taskId(), next.when()));
    }
}
