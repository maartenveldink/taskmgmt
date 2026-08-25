package eu.poc.taskmanagement.saga;

/**
 * Runs an action inside its own transaction boundary.
 *
 * <p>The deadline and provisioning process managers dispatch commands from a
 * {@code ScheduledExecutorService} thread that has no ambient
 * {@code @Transactional}.  They use this collaborator to open a fresh transaction
 * around the dispatch so the resulting event-store append is committed durably
 * (not merely published in-memory to the subscribing projections).
 *
 * <p>Abstracting the boundary behind this interface keeps the process managers
 * unit-testable without a running Quarkus/Arc container.
 */
public interface TransactionRunner {

    void runInTransaction(Runnable action);
}
