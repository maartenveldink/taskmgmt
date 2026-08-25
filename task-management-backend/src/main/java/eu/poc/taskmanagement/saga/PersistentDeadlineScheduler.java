package eu.poc.taskmanagement.saga;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Production {@link DeadlineScheduler} backed by a durable, cluster-safe
 * {@code scheduled_job} table (see {@link ScheduledJob}).
 *
 * <h2>Why this replaces the in-memory scheduler</h2>
 * The former {@code ExecutorDeadlineScheduler} held all timers in a
 * {@code ScheduledExecutorService}, which made the service impossible to run
 * safely in production:
 * <ul>
 *   <li><b>Restart recovery (#8)</b> — timers lived only in memory, so any deadline
 *       or provisioning poll that came due during downtime was lost forever.  Here
 *       every schedule is a database row, and the poller below re-discovers all due
 *       rows on startup, so timers survive a restart automatically.</li>
 *   <li><b>Cluster safety (#7)</b> — each replica kept its own timers, firing every
 *       deadline/poll once <em>per node</em>.  Here a single background poller on
 *       each node scans the shared table and claims each due job with an atomic
 *       conditional {@code UPDATE} on {@link ScheduledJob#lockedUntil}; only the node
 *       whose update affects the row runs the job, so it fires exactly once across
 *       the cluster.</li>
 * </ul>
 *
 * <h2>Execution model</h2>
 * <ol>
 *   <li>A single-threaded poller wakes every {@code poll-interval-millis}.</li>
 *   <li>It selects due, unclaimed jobs ({@code fireAt <= now} and lease free).</li>
 *   <li>Each candidate is <em>claimed</em> by a conditional update that takes a
 *       time-boxed lease — safe under concurrency because the database serialises
 *       the row update and only one node's update matches.</li>
 *   <li>The owning node runs the matching {@link ScheduledJobHandler} inside a fresh
 *       transaction and deletes the job row in that same transaction, so handler
 *       work and job removal are atomic.  On failure nothing is deleted and the
 *       lease simply expires, making the job eligible for retry (this also recovers
 *       jobs orphaned by a crashed node).</li>
 * </ol>
 */
@Slf4j
@ApplicationScoped
public class PersistentDeadlineScheduler implements DeadlineScheduler {

    private static final int CLAIM_BATCH_SIZE = 50;

    /** Identifies this JVM/node in the {@code locked_by} column (diagnostics). */
    private final String nodeId = UUID.randomUUID().toString();

    @Inject
    Instance<ScheduledJobHandler> handlerBeans;

    @ConfigProperty(name = "scheduler.persistent.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "scheduler.persistent.poll-interval-millis", defaultValue = "1000")
    long pollIntervalMillis;

    @ConfigProperty(name = "scheduler.persistent.lease-seconds", defaultValue = "60")
    long leaseSeconds;

    private Map<ScheduledJobType, ScheduledJobHandler> handlers;
    private ScheduledExecutorService poller;

    // =========================================================================
    // Lifecycle
    // =========================================================================

    void onStart(@Observes StartupEvent event) {
        if (!enabled) {
            log.info("Persistent deadline scheduler poller disabled (scheduler.persistent.enabled=false).");
            return;
        }
        handlers = new EnumMap<>(ScheduledJobType.class);
        for (ScheduledJobHandler handler : handlerBeans) {
            handlers.put(handler.type(), handler);
        }
        poller = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "persistent-deadline-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        poller.scheduleWithFixedDelay(this::runSafely, pollIntervalMillis, pollIntervalMillis, TimeUnit.MILLISECONDS);
        log.info("Persistent deadline scheduler started (node={}, pollInterval={}ms, lease={}s, handlers={}).",
                nodeId, pollIntervalMillis, leaseSeconds, handlers.keySet());
    }

    @PreDestroy
    void shutdown() {
        if (poller != null) {
            poller.shutdownNow();
        }
    }

    // =========================================================================
    // DeadlineScheduler API (called from transactional event handlers / polls)
    // =========================================================================

    @Override
    public String schedule(Instant when, ScheduledJobType type, String taskId) {
        ScheduledJob job = new ScheduledJob();
        job.id = UUID.randomUUID().toString();
        job.jobType = type;
        job.taskId = taskId;
        job.fireAt = when;
        job.persist();
        return job.id;
    }

    @Override
    public void cancel(String scheduleId) {
        if (scheduleId == null) {
            return;
        }
        ScheduledJob.deleteById(scheduleId);
    }

    @Override
    public void cancelAll(ScheduledJobType type, String taskId) {
        ScheduledJob.delete("jobType = ?1 and taskId = ?2", type, taskId);
    }

    // =========================================================================
    // Poller
    // =========================================================================

    private void runSafely() {
        try {
            pollOnce();
        } catch (RuntimeException e) {
            log.warn("Persistent scheduler poll cycle failed; will retry on next tick", e);
        }
    }

    /** One poll cycle: find due jobs, claim each, run the owned ones. */
    void pollOnce() {
        Instant now = Instant.now();
        List<ScheduledJob> due = QuarkusTransaction.requiringNew().call(() ->
                ScheduledJob.<ScheduledJob>find(
                                "fireAt <= ?1 and (lockedUntil is null or lockedUntil <= ?1)",
                                Sort.by("fireAt"), now)
                        .page(Page.ofSize(CLAIM_BATCH_SIZE))
                        .list());
        for (ScheduledJob job : due) {
            if (claim(job.id, now)) {
                execute(job);
            }
        }
    }

    /**
     * Atomically claims a job by taking a lease.  Returns {@code true} only if this
     * node's conditional update affected the row — so at most one node ever owns a
     * given job for the lease window.
     */
    private boolean claim(String id, Instant now) {
        Instant leaseUntil = now.plusSeconds(leaseSeconds);
        return QuarkusTransaction.requiringNew().call(() ->
                ScheduledJob.update(
                        "lockedUntil = ?1, lockedBy = ?2 where id = ?3 and (lockedUntil is null or lockedUntil <= ?4)",
                        leaseUntil, nodeId, id, now)) == 1;
    }

    /** Runs the handler and deletes the job atomically; leaves it for retry on failure. */
    private void execute(ScheduledJob job) {
        ScheduledJobHandler handler = handlers.get(job.jobType);
        if (handler == null) {
            log.error("No handler registered for scheduled job type {} (job {}); deleting to avoid a poison row.",
                    job.jobType, job.id);
            QuarkusTransaction.requiringNew().run(() -> ScheduledJob.deleteById(job.id));
            return;
        }
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                handler.execute(job.taskId, job.fireAt);
                ScheduledJob.deleteById(job.id);
            });
        } catch (RuntimeException e) {
            log.warn("Scheduled job {} ({} for task {}) failed; lease will expire and it will be retried",
                    job.id, job.jobType, job.taskId, e);
        }
    }
}
