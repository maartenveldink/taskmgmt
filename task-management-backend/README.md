# Task Management Backend

CQRS/Event Sourcing backend on Quarkus + Axon.

## New task type support

Tasks now support typed completion behavior:

- `STANDARD`: manual completion flow (existing behavior)
- `USER_PROVISIONING`: completion is driven by a dedicated saga that polls an external user directory and completes the task when all expected users exist

## API contract (OpenAPI-first)

`CreateTaskRequest` now includes:

- `taskType` (`STANDARD` or `USER_PROVISIONING`)
- `expectedExternalUsers` (required for `USER_PROVISIONING`, optional/empty for `STANDARD`)

The HTTP boundary uses generated OpenAPI interfaces/models. Mapping to internal commands happens in `TaskApiMapper`, then dispatching happens in `TaskCommandDispatcher`.

## External user directory integration

Configuration:

- `external.user-directory.base-url` (**required**)
- `external.user-directory.users-path-template` (default: `/tasks/{taskId}/users`)
- `external.user-directory.connect-timeout-ms` (default: `2000`)

Test profile default:

- `%test.external.user-directory.base-url=http://localhost:18081`

Expected external response: JSON array of usernames, e.g.

```json
["alice", "bob"]
```

## Saga behavior for `USER_PROVISIONING`

`UserProvisioningCompletionSaga`:

1. Starts on `TaskCreatedEvent` for `USER_PROVISIONING`
2. Waits until task is `IN_PROGRESS`
3. Polls external directory every 5 seconds
4. Sends `CompleteTaskCommand` when all expected users are present
5. Stops on terminal task states (done/cancelled/rejected) or timeout at task deadline
