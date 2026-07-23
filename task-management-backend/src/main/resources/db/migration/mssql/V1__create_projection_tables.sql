-- MSSQL version of projection tables

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'task_view')
BEGIN
    CREATE TABLE task_view (
        task_id VARCHAR(255) PRIMARY KEY,
        title VARCHAR(255) NOT NULL,
        description VARCHAR(2000),
        assigned_group VARCHAR(255) NOT NULL,
        assigned_user VARCHAR(255),
        status VARCHAR(50) NOT NULL,
        deadline DATETIME2 NOT NULL,
        created_at DATETIME2 NOT NULL,
        updated_at DATETIME2 NOT NULL
    );
END;

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'audit_trail')
BEGIN
    CREATE TABLE audit_trail (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        task_id VARCHAR(255) NOT NULL,
        event_timestamp DATETIME2 NOT NULL,
        event_type VARCHAR(100) NOT NULL,
        payload VARCHAR(4000),
        recorded_at DATETIME2 NOT NULL
    );
END;

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_audit_task_id')
BEGIN
    CREATE INDEX idx_audit_task_id ON audit_trail(task_id);
END;

