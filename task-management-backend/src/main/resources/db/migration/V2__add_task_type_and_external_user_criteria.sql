ALTER TABLE task_view ADD COLUMN IF NOT EXISTS task_type VARCHAR(50);
UPDATE task_view SET task_type = 'STANDARD' WHERE task_type IS NULL;
ALTER TABLE task_view ALTER COLUMN task_type SET NOT NULL;

ALTER TABLE task_view ADD COLUMN IF NOT EXISTS expected_external_users VARCHAR(4000);
UPDATE task_view SET expected_external_users = '[]' WHERE expected_external_users IS NULL;
