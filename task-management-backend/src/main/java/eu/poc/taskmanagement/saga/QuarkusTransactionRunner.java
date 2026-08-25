package eu.poc.taskmanagement.saga;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Production {@link TransactionRunner} that opens a new JTA transaction via
 * {@link QuarkusTransaction#requiringNew()}.
 *
 * <p>Used on the deadline / provisioning scheduler threads, which have no ambient
 * {@code @Transactional}, so the command's event-store append is persisted
 * durably rather than only published in-memory to the projections.
 */
@ApplicationScoped
public class QuarkusTransactionRunner implements TransactionRunner {

    @Override
    public void runInTransaction(Runnable action) {
        QuarkusTransaction.requiringNew().run(action::run);
    }
}
