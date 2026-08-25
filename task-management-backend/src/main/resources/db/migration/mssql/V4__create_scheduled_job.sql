-- MSSQL version of the scheduled_job table backing PersistentDeadlineScheduler.
-- Durable, cluster-safe replacement for the former in-memory scheduler timers.

IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID('scheduled_job') AND type = 'U')
BEGIN
    CREATE TABLE scheduled_job (
        id           VARCHAR(255) NOT NULL PRIMARY KEY,
        job_type     VARCHAR(50)  NOT NULL,
        task_id      VARCHAR(255) NOT NULL,
        fire_at      DATETIME2    NOT NULL,
        locked_until DATETIME2,
        locked_by    VARCHAR(255)
    );
END;

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_scheduled_job_fire_at' AND object_id = OBJECT_ID('scheduled_job'))
BEGIN
    CREATE INDEX idx_scheduled_job_fire_at ON scheduled_job (fire_at);
END;
