-- Durable, cluster-safe scheduler table backing PersistentDeadlineScheduler.
-- Replaces the former in-memory ScheduledExecutorService timers so that
-- deadline/provisioning-poll schedules survive a restart and can be claimed by
-- exactly one node in a multi-instance deployment.

CREATE TABLE IF NOT EXISTS scheduled_job (
    id           VARCHAR(255) PRIMARY KEY,
    job_type     VARCHAR(50)  NOT NULL,
    task_id      VARCHAR(255) NOT NULL,
    fire_at      TIMESTAMP    NOT NULL,
    locked_until TIMESTAMP,
    locked_by    VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_scheduled_job_fire_at ON scheduled_job (fire_at);
