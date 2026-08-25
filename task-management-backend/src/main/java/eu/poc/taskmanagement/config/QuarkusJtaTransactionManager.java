package eu.poc.taskmanagement.config;

import org.axonframework.common.jpa.EntityManagerExecutor;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.conversion.CachingSupplier;
import org.axonframework.messaging.core.unitofwork.ProcessingLifecycle;
import org.axonframework.messaging.core.unitofwork.transaction.Transaction;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionManager;
import org.axonframework.messaging.core.unitofwork.transaction.jpa.JpaTransactionalExecutorProvider;

/**
 * Axon {@link TransactionManager} that integrates the Axon 5 JPA event store
 * with Quarkus-managed JTA transactions.
 *
 * <h2>Why a custom manager</h2>
 * Axon 5's event store reads and writes through a
 * {@link JpaTransactionalExecutorProvider}, which expects a
 * {@link EntityManagerExecutor} supplier to be present in the Axon
 * {@code ProcessingContext} (populated by the active {@link TransactionManager}).
 *
 * <p>Axon's built-in {@code EntityManagerTransactionManager} would call
 * {@code EntityManager.getTransaction()} to begin/commit a <em>resource-local</em>
 * transaction.  That is illegal on a JTA-managed {@code EntityManager} such as
 * the one Quarkus provides, and would throw at runtime.
 *
 * <h2>Behaviour</h2>
 * This manager mirrors the ambient JTA transaction:
 * <ul>
 *   <li><b>REST / command path</b> — command dispatch is wrapped in
 *       {@code @Transactional} at the REST layer, so a JTA transaction is already
 *       active.  {@link #startTransaction()} then <em>joins</em> it and returns a
 *       no-op {@link Transaction}; Narayana (driven by Quarkus) commits.</li>
 *   <li><b>Scheduler-thread path</b> — the deadline / provisioning process
 *       managers dispatch commands from a {@code ScheduledExecutorService} thread
 *       that has <em>no</em> ambient transaction.  They therefore wrap the
 *       dispatch in {@code QuarkusTransaction.requiringNew().run(...)}, which opens
 *       a fresh JTA transaction before the command reaches Axon.  {@link
 *       #startTransaction()} then <em>joins</em> that transaction, so the
 *       event-store append is persisted durably (not just published in-memory to
 *       the subscribing projections).</li>
 * </ul>
 * The transaction-scoped {@code EntityManager} proxy supplied via
 * {@link #attachToProcessingLifecycle(ProcessingLifecycle)} binds to whichever
 * JTA transaction is active on the thread, so the event store always writes into
 * the correct transaction.
 */
public class QuarkusJtaTransactionManager implements TransactionManager {

    /** Commit/rollback of the ambient JTA transaction is owned by Quarkus, never by Axon. */
    private static final Transaction JOINED_TRANSACTION = new Transaction() {
        @Override
        public void commit() {
            // JTA transaction is committed by its owner (REST @Transactional or
            // the QuarkusTransaction.requiringNew() wrapper on the scheduler thread).
        }

        @Override
        public void rollback() {
            // JTA transaction is rolled back by its owner.
        }
    };

    private final EntityManagerProvider entityManagerProvider;

    public QuarkusJtaTransactionManager(EntityManagerProvider entityManagerProvider) {
        this.entityManagerProvider = entityManagerProvider;
    }

    @Override
    public Transaction startTransaction() {
        // A JTA transaction is always active by the time a command reaches Axon:
        // either from the REST layer's @Transactional or from the
        // QuarkusTransaction.requiringNew() wrapper on the scheduler thread.
        // Join it and leave commit/rollback to its owner.
        return JOINED_TRANSACTION;
    }

    @Override
    public void attachToProcessingLifecycle(ProcessingLifecycle lifecycle) {
        lifecycle.runOnPreInvocation(context ->
                context.putResource(
                        JpaTransactionalExecutorProvider.SUPPLIER_KEY,
                        CachingSupplier.of(() -> new EntityManagerExecutor(entityManagerProvider))));
    }
}
