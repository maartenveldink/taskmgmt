package eu.poc.taskmanagement.saga;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Production {@link DeadlineScheduler} backed by a {@link ScheduledExecutorService}.
 *
 * <p>This replaces the Axon 4 {@code QuartzDeadlineManager} + standalone Quartz
 * scheduler.  Like the previous Quartz RAM job store, schedules are held in
 * memory only and are lost on restart — acceptable for this PoC (the H2 event
 * store is also in-memory).
 */
@Slf4j
@ApplicationScoped
public class ExecutorDeadlineScheduler implements DeadlineScheduler {

    private final ScheduledExecutorService executor =
            Executors.newScheduledThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "task-deadline-scheduler");
                thread.setDaemon(true);
                return thread;
            });

    private final Map<String, ScheduledFuture<?>> scheduled = new ConcurrentHashMap<>();

    @Override
    public String schedule(Instant when, Runnable task) {
        String id = UUID.randomUUID().toString();
        long delayMillis = Math.max(0, Duration.between(Instant.now(), when).toMillis());
        ScheduledFuture<?> future = executor.schedule(() -> {
            scheduled.remove(id);
            try {
                task.run();
            } catch (RuntimeException e) {
                log.error("Scheduled deadline task {} failed", id, e);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
        scheduled.put(id, future);
        return id;
    }

    @Override
    public void cancel(String scheduleId) {
        if (scheduleId == null) {
            return;
        }
        ScheduledFuture<?> future = scheduled.remove(scheduleId);
        if (future != null) {
            future.cancel(false);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
