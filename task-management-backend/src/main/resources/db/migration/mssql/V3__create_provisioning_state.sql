-- MSSQL version of the provisioning_state process-manager table

IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID('provisioning_state') AND type = 'U')
BEGIN
    CREATE TABLE provisioning_state (
        task_id VARCHAR(255) NOT NULL PRIMARY KEY,
        deadline DATETIME2 NOT NULL,
        last_known_status VARCHAR(50) NOT NULL,
        expected_users VARCHAR(4000) NOT NULL,
        schedule_id VARCHAR(255),
        version BIGINT NOT NULL DEFAULT 0
    );
END;
