# Task Management System — Requirements

> **Source**: `docs/task-management-story.md`
> **Domain**: CQRS / Axon Framework PoC — TaskManagement
> **Date**: 2026-03-23
> **Status**: Final — v1.4 (technical decisions added; ready for implementation)

---

## Technical Decisions

| Concern | Decision |
|---|---|
| Event store | Axon JPA event store backed by H2 in-memory database |
| Read model / audit trail DB | H2 in-memory via JPA (Hibernate ORM with Panache) |
| Saga deadline scheduler | Quarkus/Quartz — document configuration thoroughly in code and in `docs/` |
| Deployment target | Local (run directly or Docker Compose); Kubernetes is out of scope for this PoC |
| Task identity | External `correlationId` provided by the calling system is used as the aggregate ID |
| REST API style | Verb-based endpoints (e.g., `POST /tasks/{id}/start`) |
| Command handling | Synchronous — REST response waits for command handling confirmation |
| Initial assignee | Task is created without a specific user; a default user group is always applied if no group is specified in the command. Configured via `task.default-group=unassigned` in `application.yaml` |
| `StartTaskCommand` precondition | Any task in status `Assigned` may be started regardless of whether the assignee is a user or a group |
| Build tool | Maven |
| Base Java package | `eu.poc.taskmanagement` |
| Quarkus extensions | RESTEasy Reactive, Hibernate ORM with Panache, Quartz, Axon Quarkus Extension |

---

## Scope & Constraints

- Authentication and authorisation are out of scope.
- User and group management is external; this system only stores user names and group names as strings for assignment purposes.
- Notifications to external systems are out of scope; the system publishes a `TaskDeadlineExceededEvent` for future consumption.
- Escalation logging uses the standard application logging framework (no dedicated event or command).
- No task priority concept.
- Deadline extension (after task creation) is out of scope for this PoC; deferred to a future release.
- The PoC is not expected to meet production-grade security or performance requirements.

---

## Task Lifecycle

```
Created → Assigned → In Progress → Done
                                 → Cancelled
          → Rejected
```

- `Done`, `Cancelled`, and `Rejected` are terminal states. No further state transitions are permitted.
- `Cancelled` represents a task stopped by an internal user after assignment.
- `Rejected` represents a task refused before or during assignment (e.g., invalid request from the external system).

---

## 1. Command Handler Requirements

### CH-01 — Create Task

**Given** an external system sends a `CreateTaskCommand` containing a mandatory `correlationId` (used as the aggregate ID), a mandatory task title, description, mandatory deadline, and an optional group name,
**When** the Command Handler processes the command,
**Then** a new Task aggregate is created with the provided `correlationId` as its ID, a `TaskCreatedEvent` is published, and the task is persisted in the event store with status `Created`. If no group name is provided, the system applies the group name configured under `task.default-group` in `application.yaml` (default value: `unassigned`).

---

### CH-02 — Assign Task to User or Group

**Given** a task exists and is not in a terminal state (`Done`, `Cancelled`, `Rejected`),
**When** an `AssignTaskCommand` is dispatched with either a user name or a group name,
**Then** the Task aggregate updates the assignee, publishes a `TaskAssignedEvent` containing the assignee type (`USER` or `GROUP`) and the assignee name, and the task status transitions to `Assigned`.

---

### CH-03 — Reassign Task

**Given** a task exists and has a current assignee,
**When** a `ReassignTaskCommand` is dispatched with a new user name or group name,
**Then** the Task aggregate updates the assignee, publishes a `TaskReassignedEvent` containing the previous assignee and the new assignee (both type and name), and the status remains unchanged.

---

### CH-04 — Assign Task to Specific User within Group

**Given** a task is assigned to a group,
**When** a `ReassignTaskCommand` is dispatched targeting a specific user name (with an optional reference to the originating group),
**Then** the Task aggregate updates the assignee to the individual user, publishes a `TaskReassignedEvent`, and the status remains unchanged.

---

### CH-05 — Set Task In Progress

**Given** a task is in status `Assigned`, regardless of whether the assignee is a user or a group,
**When** a `StartTaskCommand` is dispatched for that task,
**Then** the Task aggregate transitions to status `In Progress` and publishes a `TaskStartedEvent`.

---

### CH-06 — Complete Task

**Given** a task is in status `In Progress`,
**When** a `CompleteTaskCommand` is dispatched for that task,
**Then** the Task aggregate transitions to status `Done` and publishes a `TaskCompletedEvent`.

---

### CH-07 — Cancel Task

**Given** a task exists and is not in a terminal state (`Done`, `Cancelled`, `Rejected`),
**When** a `CancelTaskCommand` is dispatched with an optional reason,
**Then** the Task aggregate transitions to status `Cancelled` and publishes a `TaskCancelledEvent` containing the optional cancellation reason.

---

### CH-08 — Reject Task

**Given** a task exists and is in status `Created` or `Assigned`,
**When** a `RejectTaskCommand` is dispatched with an optional reason,
**Then** the Task aggregate transitions to status `Rejected` and publishes a `TaskRejectedEvent` containing the optional rejection reason.

---

### CH-09 — Reject invalid state transitions

**Given** a task is in a terminal state (`Done`, `Cancelled`, or `Rejected`),
**When** any state-changing command (`StartTaskCommand`, `CompleteTaskCommand`, `AssignTaskCommand`, etc.) is dispatched on that task,
**Then** the Command Handler rejects the command with a descriptive exception and no event is published.

---

### CH-10 — Reject commands for unknown tasks

**Given** no task exists with the provided task ID,
**When** any command targeting that task ID is dispatched,
**Then** the Command Handler returns a not-found error and no event is published.

---

### CH-11 — Idempotency guard on task creation

**Given** a `CreateTaskCommand` carries a `correlationId` that already exists as an aggregate ID in the event store,
**When** the Command Handler receives the duplicate command,
**Then** the command is rejected with a `409 Conflict` response without creating a duplicate aggregate or publishing duplicate events.

---

### CH-12 — Deadline and correlationId are mandatory on task creation

**Given** an external system sends a `CreateTaskCommand` without a deadline or without a `correlationId`,
**When** the Command Handler validates the command,
**Then** the command is rejected with a `400 Bad Request` indicating which mandatory field is missing.

---

## 2. Event Handler Requirements

### EH-01 — Build read model on TaskCreated

**Given** a `TaskCreatedEvent` is published to the event bus,
**When** the Event Handler processes the event,
**Then** a new record is inserted into the read model with all task fields (ID, title, description, assignee, deadline) and status `Created`.

---

### EH-02 — Update read model on TaskAssigned / TaskReassigned

**Given** a `TaskAssignedEvent` or `TaskReassignedEvent` is published,
**When** the Event Handler processes the event,
**Then** the read model entry for that task is updated with the new assignee type and name.

---

### EH-03 — Update read model on status change

**Given** a `TaskStartedEvent`, `TaskCompletedEvent`, `TaskCancelledEvent`, or `TaskRejectedEvent` is published,
**When** the Event Handler processes the event,
**Then** the read model entry for that task is updated to reflect the new status.

---

### EH-04 — Query tasks per user

**Given** one or more tasks are assigned to a specific user,
**When** a `GetTasksByUserQuery` is dispatched with a valid user name,
**Then** the Query Handler returns all tasks assigned to that user from the read model.

---

### EH-05 — Query tasks per group

**Given** one or more tasks are assigned to a user group,
**When** a `GetTasksByGroupQuery` is dispatched with a valid group name,
**Then** the Query Handler returns all tasks assigned to that group from the read model.

---

### EH-06 — Filter tasks by status

**Given** tasks exist in the system with various statuses,
**When** a query is dispatched with a `status` filter parameter (e.g., `In Progress`, `Done`, `Cancelled`, `Rejected`),
**Then** the Query Handler returns only tasks matching the specified status.

---

### EH-07 — Filter tasks by deadline

**Given** tasks exist in the system with various deadlines,
**When** a query is dispatched with a `deadlineBefore` and/or `deadlineAfter` filter parameter,
**Then** the Query Handler returns only tasks whose deadline falls within the specified range.

---

### EH-08 — REST API entry point for commands

**Given** an external system or user sends an HTTP request to a verb-based REST endpoint,
**When** the request contains a valid command payload,
**Then** the API dispatches the corresponding command to the Command Gateway synchronously and returns an appropriate HTTP response. Endpoint mapping:

| HTTP Method | Path | Command |
|---|---|---|
| `POST` | `/tasks` | `CreateTaskCommand` |
| `POST` | `/tasks/{id}/assign` | `AssignTaskCommand` |
| `POST` | `/tasks/{id}/reassign` | `ReassignTaskCommand` |
| `POST` | `/tasks/{id}/start` | `StartTaskCommand` |
| `POST` | `/tasks/{id}/complete` | `CompleteTaskCommand` |
| `POST` | `/tasks/{id}/cancel` | `CancelTaskCommand` |
| `POST` | `/tasks/{id}/reject` | `RejectTaskCommand` |

Response codes: `200` on success, `400` for validation errors, `404` for unknown task IDs, `409 Conflict` for invalid state transitions, `5xx` on server error.

---

### EH-09 — REST API entry point for queries

**Given** a user or system sends an HTTP GET request to the REST API with optional filter parameters,
**When** the request is processed,
**Then** the API dispatches the corresponding query to the Query Gateway and returns the matching result as JSON. Endpoint mapping:

| HTTP Method | Path | Query | Optional filters |
|---|---|---|---|
| `GET` | `/tasks` | All tasks | `?status=`, `?deadlineBefore=`, `?deadlineAfter=` |
| `GET` | `/tasks/user/{userName}` | `GetTasksByUserQuery` | `?status=`, `?deadlineBefore=`, `?deadlineAfter=` |
| `GET` | `/tasks/group/{groupName}` | `GetTasksByGroupQuery` | `?status=`, `?deadlineBefore=`, `?deadlineAfter=` |
| `GET` | `/tasks/{id}/audit` | `GetAuditTrailByTaskQuery` | — |

---

### EH-10 — Read model consistency after replay

**Given** the event store contains all historical events for tasks,
**When** the read model projection is rebuilt by replaying all events from the beginning,
**Then** the resulting read model is identical in content to the one built by processing events in real time.

---

## 3. Deadline Management Requirements

### DM-01 — Start Saga on task creation

**Given** a `TaskCreatedEvent` is published containing a mandatory deadline timestamp,
**When** the Axon Saga is triggered by this event,
**Then** the Saga starts, associates itself with the task ID, and schedules a deadline trigger for the exact timestamp specified in the event.

---

### DM-02 — No escalation when task completed on time

**Given** a Saga is active and monitoring the deadline for a task,
**When** a `TaskCompletedEvent` is received for that task before the scheduled deadline fires,
**Then** the Saga cancels the scheduled deadline trigger and terminates cleanly without logging an escalation.

---

### DM-03 — No escalation when task cancelled or rejected

**Given** a Saga is active and monitoring the deadline for a task,
**When** a `TaskCancelledEvent` or `TaskRejectedEvent` is received for that task before the deadline fires,
**Then** the Saga cancels the scheduled deadline trigger and terminates cleanly.

---

### DM-04 — Log escalation on deadline exceeded

**Given** a Saga is active and the deadline trigger fires,
**When** the task is not in status `Done`, `Cancelled`, or `Rejected` at the time of the deadline,
**Then** the Saga logs a warning using the standard application logging framework containing at minimum: the task ID, the deadline timestamp, and the current task status.

---

### DM-05 — Publish deadline exceeded event for external systems

**Given** the deadline has been exceeded and the escalation has been logged,
**When** the Saga processes the expired deadline,
**Then** the Saga publishes a `TaskDeadlineExceededEvent` containing the task ID and the deadline timestamp, enabling future external systems to subscribe and act on this event.

---

### DM-06 — Deadline is immutable after creation

**Given** a task has been created with a deadline,
**When** no command exists to change the deadline,
**Then** the Saga always evaluates against the original deadline set at task creation time, and no other event or command can alter the scheduled deadline trigger.

---

### DM-07 — Saga handles missing or already-terminated task gracefully

**Given** a Saga is active for a task,
**When** the deadline fires but the task aggregate is not found or is already in a terminal state,
**Then** the Saga logs an error with the task ID and terminates without publishing a `TaskDeadlineExceededEvent` or retrying.

---

### DM-08 — Short deadline support for testing

**Given** a `CreateTaskCommand` is issued with a deadline only seconds in the future,
**When** the deadline expires,
**Then** the Saga correctly triggers the deadline logic within the expected time window, verifiable in an automated test without modifying production Saga code.

---

## 4. Audit Trail Requirements

### AT-01 — Register every domain event in the audit trail

**Given** any domain event (`TaskCreatedEvent`, `TaskAssignedEvent`, `TaskReassignedEvent`, `TaskStartedEvent`, `TaskCompletedEvent`, `TaskCancelledEvent`, `TaskRejectedEvent`, `TaskDeadlineExceededEvent`) is published for a task,
**When** the Audit Trail Event Handler processes the event,
**Then** a new entry is appended to the audit trail projection containing: task ID, event timestamp, event type, and the relevant event payload.

---

### AT-02 — Query audit trail per task

**Given** a task has one or more audit trail entries,
**When** a `GetAuditTrailByTaskQuery` is dispatched with a valid task ID,
**Then** the system returns all audit trail entries for that task in strict chronological order (oldest first).

---

### AT-03 — Audit trail is append-only

**Given** an audit trail entry has been written,
**When** any subsequent event or command is processed,
**Then** existing audit trail entries are never modified or deleted — only new entries are appended.

---

### AT-04 — Audit trail payload for assignment events

**Given** a `TaskAssignedEvent` or `TaskReassignedEvent` is processed by the audit trail handler,
**When** the entry is stored,
**Then** the payload includes: the assignee type (`USER` or `GROUP`), the new assignee name, and (for reassignment) the previous assignee type and name.

---

### AT-05 — Audit trail payload for status change events

**Given** a status-change event (`TaskStartedEvent`, `TaskCompletedEvent`, `TaskCancelledEvent`, `TaskRejectedEvent`) is processed,
**When** the audit trail entry is stored,
**Then** the payload includes: the previous status and the new status. For `TaskCancelledEvent` and `TaskRejectedEvent`, the optional reason is also included if present.

---

### AT-06 — Audit trail payload for deadline exceeded event

**Given** a `TaskDeadlineExceededEvent` is processed by the audit trail handler,
**When** the entry is stored,
**Then** the payload includes: the task ID, the original deadline timestamp, and the task status at the time the deadline was exceeded.

---

### AT-07 — Audit trail survives projection replay

**Given** the event store contains all historical events,
**When** the audit trail projection is replayed from scratch,
**Then** the resulting audit trail is identical to the original: same entries, same content, in the same chronological order.

---

### AT-08 — Audit trail entry for task creation includes full context

**Given** a `TaskCreatedEvent` is processed by the audit trail handler,
**When** the entry is stored,
**Then** the payload includes: task title, description, initial assignee (type and name), and the mandatory deadline.

---

## Resolved Decisions

| # | Question | Decision |
|---|---|---|
| Q1 | Can the deadline be extended after creation? | Out of scope for this PoC; deferred to a future release. |
| Q2 | Should `TaskDeadlineExceededEvent` be published when the task is already `Cancelled` or `Rejected`? | No — terminal states make the deadline irrelevant. Saga terminates cleanly (see DM-03, DM-07). |
| Q3 | Is there a maximum number of reassignments allowed on a single task? | No limit. |
| Q4 | HTTP status code for invalid state transitions? | `409 Conflict` (see EH-08). |
| Q5 | Should the read model expose the full task history, or only the latest state? | Latest state only. Full history is covered by the audit trail (see AT-02). |
| Q6 | Are there requirements for pagination on query results? | No pagination for this PoC. |
| Q7 | Is the task ID generated by our system or supplied externally? | Supplied externally as `correlationId`; used as the aggregate ID. |
| Q8 | Is the initial assignee mandatory in `CreateTaskCommand`? | No. A configurable default user group is applied if no group is specified. |
| Q9 | Does `StartTaskCommand` require the task to be assigned to a specific user? | No. A group-assigned task can be started directly. |
