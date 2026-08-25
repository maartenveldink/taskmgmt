CREATE TABLE IF NOT EXISTS provisioning_state (
    task_id VARCHAR(255) PRIMARY KEY,
    deadline TIMESTAMP NOT NULL,
    last_known_status VARCHAR(50) NOT NULL,
    expected_users VARCHAR(4000) NOT NULL,
    schedule_id VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0
);
