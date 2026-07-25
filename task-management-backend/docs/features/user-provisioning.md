# User provisioning completion flow

The `USER_PROVISIONING` task type is completed by a dedicated saga.

## Flow

1. A task is created with `taskType=USER_PROVISIONING`.
2. The create command must include `expectedExternalUsers`.
3. When the task starts, `UserProvisioningCompletionSaga` begins polling the external user directory.
4. When all expected users exist, the saga dispatches `CompleteTaskCommand`.
5. The saga stops when the task reaches a terminal state or the deadline is exceeded.

## External directory contract

The external service must return a JSON array of usernames:

```json
["alice", "bob"]
```

## Configuration

- `external.user-directory.base-url` — required
- `external.user-directory.users-path-template` — defaults to `/tasks/{taskId}/users`
- `external.user-directory.connect-timeout-ms` — defaults to `2000`

## Notes

- Polling interval: 5 seconds
- Saga timeout: task deadline
