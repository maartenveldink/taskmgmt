# Handoff — deadline/provisioning escalation is not durably persisted (JTA tx bug)

**Date:** 2026-08-25
**Branch:** `main` (all migration work is **uncommitted** in the working tree)
**Scope:** `task-management-backend`

---

## 0. ⚠️ FIRST: repair the accidental revert of AxonConfig.java

While reverting an experimental fix I ran `git checkout -- .../config/AxonConfig.java`.
Because the **entire Axon 4 → 5 migration is uncommitted**, that command did **not**
restore "the pre-fix working state" — it restored the committed **Axon 4** version
from HEAD. `AxonConfig.java` is now the OLD Quartz/DefaultConfigurer version and is
**incompatible** with the rest of the working tree (which is Axon 5). The project will
**not compile** until this is fixed.

Verify the broken state:
```bash
grep -c "QuartzDeadlineManager" task-management-backend/src/main/java/eu/poc/taskmanagement/config/AxonConfig.java
# > 0  → still the old Axon 4 file, must be restored
```

### Restore the Axon 5 version
A full `git diff HEAD` snapshot captured at the start of the review is saved at
`/tmp/backend_review.diff` (≈134 KB, verified intact). It contains the complete
Axon 5 version of `AxonConfig.java` as the `+` side of its hunk.

**Recommended recovery** — extract just the AxonConfig hunk and apply it onto the
current (HEAD/Axon 4) file:
```bash
cd /Users/maartenveldink/projects/ordina/claude/taskmgmt

awk '/^diff --git a\/task-management-backend\/src\/main\/java\/eu\/poc\/taskmanagement\/config\/AxonConfig.java/{f=1} /^diff --git a\/task-management-backend\/src\/main\/java\/eu\/poc\/taskmanagement\/config\/AxonResourceInjector.java/{f=0} f{print}' \
  /tmp/backend_review.diff > /tmp/axonconfig.patch

git apply /tmp/axonconfig.patch
```
Then confirm it is back to Axon 5:
```bash
grep -c "EventSourcingConfigurer" task-management-backend/src/main/java/eu/poc/taskmanagement/config/AxonConfig.java   # should be > 0
grep -c "QuartzDeadlineManager"    task-management-backend/src/main/java/eu/poc/taskmanagement/config/AxonConfig.java   # should be 0
```

If `git apply` fails, the entire correct Axon 5 content is readable in
`/tmp/backend_review.diff` (search for `AxonConfig.java`) — reconstruct by hand.

> NOTE: If `/tmp/backend_review.diff` is gone, regenerate understanding from the other
> untracked files; but the AxonConfig Axon 5 source only exists in that diff and in
> nowhere else on disk. Recover it **before** doing anything else.

The Axon 5 `AxonConfig` registers a `QuarkusJtaTransactionManager` component like:
```java
.componentRegistry(cr -> cr.registerComponent(
        TransactionManager.class,
        c -> new QuarkusJtaTransactionManager(entityManagerProvider)))
```
Make sure the constructor call matches whatever signature `QuarkusJtaTransactionManager`
ends up with (see §4).

---

## 1. What we were doing

Reviewing a large **uncommitted** migration of the backend from **Axon Framework
4.9.3 → 5.3.1**. As part of it, the two Axon sagas + `QuartzDeadlineManager` were
replaced by plain CDI **process managers** with in-memory state and a custom
`ScheduledExecutorService`:

- `saga/TaskDeadlineProcessManager.java` (new, untracked)
- `saga/UserProvisioningProcessManager.java` (new, untracked)
- `saga/DeadlineScheduler.java` + `saga/ExecutorDeadlineScheduler.java` (new)
- `config/QuarkusJtaTransactionManager.java` (new)
- `model/command/MarkDeadlineExceededCommand.java` (new)

User asked to: build + run tests, then fix review finding **#1**, and add test
coverage so the bug can't regress.

## 2. Build/test baseline (before any changes)

`mvn -o test` → **BUILD SUCCESS, 70 tests, 0 failures.** So the migration compiles
and the happy-path suite is green. The process-manager unit tests use a
`FakeDeadlineScheduler` that fires callbacks on the **test thread**, so they do **not**
exercise the real scheduler-thread + transaction path.

## 3. The confirmed bug (finding #1, refined)

**Symptom:** When a task's deadline elapses, `TaskDeadlineProcessManager.fireDeadline()`
runs on a daemon scheduler thread (`task-deadline-scheduler`) that has **no ambient
`@Transactional`**. It dispatches `MarkDeadlineExceededCommand`; the aggregate appends
`TaskDeadlineExceededEvent`.

The event **is** delivered in-memory to the subscribing `AuditTrailProjection` (which
has its **own** `@Transactional`, so the audit row commits) — but the event is **NOT
durably written to the event store** (`AggregateEventEntry`). Event store and read
model diverge; on replay/restart the escalation event is lost. This is a real
event-sourcing consistency bug.

**Proven empirically.** A probe `@QuarkusTest` created a task with a 1s deadline, waited
for the escalation, then asserted the event-store count:
```
expected: <4> but was: <3>     // created+assigned+started+deadlineExceeded → only 3 stored
```
The same defect applies to `UserProvisioningProcessManager.poll()` dispatching
`CompleteTaskCommand` from the scheduler thread (not separately tested yet).

**Root cause:** The old `AxonConfig.buildTransactionManager()` began a **new JTA
transaction** when none was active (the Quartz worker-thread path). The Axon 5
replacement `QuarkusJtaTransactionManager.startTransaction()` returns a **no-op**
transaction and relies entirely on the REST layer's `@Transactional`. On the scheduler
thread there is no such transaction, so the event-store append never commits.

## 4. Fix attempts & current state of the fix

### Attempt A (INSUFFICIENT — do not stop here)
Changed `QuarkusJtaTransactionManager` to take a `UserTransaction` and, in
`startTransaction()`, **begin/commit a new JTA tx when none is active** (join otherwise).
Wired `UserTransaction` into `AxonConfig`.
→ Re-ran the regression test: **still `expected 4 but was 3`.** So Axon 5's command
UnitOfWork does not invoke our `TransactionManager.startTransaction()` in a way that
wraps the event-store append on that thread. **This alone does not fix it.**

> The `AxonConfig` part of Attempt A was lost in the accidental revert (§0). The
> **`QuarkusJtaTransactionManager.java`** file on disk still contains the Attempt-A
> version (constructor `(EntityManagerProvider, UserTransaction)`). Decide in §5 whether
> to keep or revert it; if you revert it to the original single-arg constructor, update
> the `registerComponent` call in `AxonConfig` accordingly.

### Attempt B (RECOMMENDED — not yet implemented)
Replicate the REST layer's ambient transaction on the scheduler thread by wrapping the
command dispatch in an explicit JTA transaction, using
`io.quarkus.narayana.jta.QuarkusTransaction`:

```java
// in TaskDeadlineProcessManager.fireDeadline(...)
QuarkusTransaction.requiringNew().run(() ->
        commandGateway.sendAndWait(new MarkDeadlineExceededCommand(taskId, state.deadline)));

// in UserProvisioningProcessManager.poll(...)  (the CompleteTaskCommand dispatch)
QuarkusTransaction.requiringNew().run(() ->
        commandGateway.sendAndWait(new CompleteTaskCommand(taskId)));
```
Rationale: the REST path works purely because `@Transactional` provides an ambient JTA
tx that the transaction-scoped `EntityManager` proxy binds to. `requiringNew()` gives the
scheduler thread the same ambient tx, independent of Axon UoW internals. Quarkus version
is 3.32.4 (`QuarkusTransaction.requiringNew()` builder API is available).

Open design choice: put the boundary in the two process managers (explicit, at the
dispatch site) **or** centralize it in `ExecutorDeadlineScheduler` by wrapping every
scheduled `task.run()` in `QuarkusTransaction.requiringNew()`. The scheduler is a generic
abstraction, so wrapping there couples it to JTA; the process-manager site is more
honest. Prefer the process-manager site unless you want all future scheduled callbacks
transactional by default.

After implementing B, decide whether to also keep the improved
`QuarkusJtaTransactionManager` (it is strictly more correct than the original no-op —
it joins an existing tx and would own one if ever called — so keeping it is fine, but
it is not what makes the fix work).

## 5. Regression test (already in place — keep it)

`src/test/java/.../api/TaskBackendFlowTest.java` was extended (the change survived the
revert) with a durable-store assertion after escalation:
```java
assertEquals(4L, queryStore.countDomainEvents(taskId),
    "TaskDeadlineExceededEvent must be persisted in the event store, not only in the projection");
```
This test **currently FAILS** (`expected 4 but was 3`) and is the guard for finding #1.
It must go green once Attempt B is implemented.

TODO for additional coverage:
- Add an equivalent E2E guard for `UserProvisioningProcessManager` — that the
  scheduler-thread `CompleteTaskCommand` produces a **durably stored** `TaskCompletedEvent`
  (count includes the completion event). Requires controlling `ExternalUserDirectoryClient`
  in a `@QuarkusTest` (mock/`@InjectMock` or a test profile) so all expected users are
  "created". This path is currently only covered by the unit test with the fake scheduler.

## 6. Definition of done

1. `AxonConfig.java` restored to the Axon 5 version (§0); project compiles.
2. Attempt B implemented (deadline **and** provisioning dispatch wrapped in JTA tx).
3. `mvn -o test` green, **including** the new `TaskBackendFlowTest` durable-store
   assertion (count == 4).
4. New provisioning durable-store E2E guard added and green.
5. Re-review the other findings from the original review that were NOT yet addressed
   (they are independent of #1):
   - **#2** timers/in-memory state scheduled before the command tx commits → phantom
     deadlines on rollback.
   - **#3** data race on the non-volatile `DeadlineState`/`ProvisioningState` fields
     read on the scheduler thread vs written on the event-processor thread.
   - **#4** `end()` vs `scheduleNextPoll()` race can leak one extra poll.
   - **#5** all deadline/provisioning state is purely in-memory (no recovery on restart)
     — documented as acceptable for the PoC.

## 7. Useful commands

```bash
# from repo root; mvn is at /opt/homebrew/bin/mvn, no wrapper present
mvn -o test                                   # full suite (offline)
mvn -o test -Dtest=TaskBackendFlowTest        # the #1 regression guard
```
Files of interest:
- `config/QuarkusJtaTransactionManager.java`  (currently Attempt-A version)
- `config/AxonConfig.java`                     (**must be restored**, see §0)
- `saga/TaskDeadlineProcessManager.java`       (`fireDeadline` = dispatch site)
- `saga/UserProvisioningProcessManager.java`   (`poll` = dispatch site)
- `test/.../api/TaskBackendFlowTest.java`       (regression guard, currently red)
- `/tmp/backend_review.diff`                    (full start-of-review snapshot)
```
