-- MSSQL version of task_type and external_user_criteria columns

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('task_view') AND name = 'task_type')
BEGIN
    ALTER TABLE task_view ADD task_type VARCHAR(50);
END;
GO

UPDATE task_view SET task_type = 'STANDARD' WHERE task_type IS NULL;
GO

ALTER TABLE task_view ALTER COLUMN task_type VARCHAR(50) NOT NULL;
GO

IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('task_view') AND name = 'expected_external_users')
BEGIN
    ALTER TABLE task_view ADD expected_external_users VARCHAR(4000);
END;
GO

UPDATE task_view SET expected_external_users = '[]' WHERE expected_external_users IS NULL;

