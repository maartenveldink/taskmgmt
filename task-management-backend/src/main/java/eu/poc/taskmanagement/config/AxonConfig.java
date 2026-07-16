package eu.poc.taskmanagement.config;

import eu.poc.taskmanagement.model.TaskAggregate;
import eu.poc.taskmanagement.projection.audittrail.AuditTrailProjection;
import eu.poc.taskmanagement.projection.tasks.TaskProjection;
import eu.poc.taskmanagement.integration.userdirectory.ExternalUserDirectoryClient;
import eu.poc.taskmanagement.saga.TaskDeadlineSaga;
import eu.poc.taskmanagement.saga.UserProvisioningCompletionSaga;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Status;
import jakarta.transaction.UserTransaction;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.Configuration;
import org.axonframework.config.Configurer;
import org.axonframework.config.DefaultConfigurer;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.quartz.QuartzDeadlineManager;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.axonframework.messaging.interceptors.BeanValidationInterceptor;
import org.axonframework.modelling.saga.repository.jpa.JpaSagaStore;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.annotation.AnnotationQueryHandlerAdapter;
import org.axonframework.serialization.json.JacksonSerializer;
import org.quartz.Scheduler;
import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;
import lombok.extern.slf4j.Slf4j;
import java.util.Properties;

/**
 * Axon Framework CDI configuration for Quarkus.
 *
 * <h2>Overview</h2>
 * This class manually wires the Axon Framework components using the
 * {@link DefaultConfigurer} API — the equivalent of what
 * {@code @EnableAxon} / {@code axon-spring-boot-autoconfigure} does in
 * a Spring Boot application.
 *
 * <p>Because Quarkus uses CDI (not Spring), there is no auto-configuration.
 * Every component is declared here and the resulting {@link Configuration} is
 * stored as an application-scoped CDI bean.  The {@link CommandGateway},
 * {@link QueryGateway}, {@link DeadlineManager}, and {@link EventBus} are
 * exposed as individual {@code @Produces} beans so the rest of the
 * application can inject them directly.
 *
 * <h2>Component map</h2>
 * <pre>
 *  JacksonSerializer          — serialises commands, events, saga state
 *  JpaEventStorageEngine      — stores domain events in H2 via JPA
 *  JpaSagaStore               — stores saga state (incl. TaskDeadlineSaga)
 *  QuartzDeadlineManager      — schedules deadlines via Quarkus Quartz
 *  SimpleCommandBus           — synchronous command handling
 *  SubscribingEventProcessor  — synchronous event delivery to projections
 *  SimpleQueryBus             — synchronous query handling
 *  CdiResourceInjector        — injects CDI beans into Saga instances
 * </pre>
 *
 * <h2>Transaction model</h2>
 * The REST layer annotates command-dispatching methods with
 * {@code @Transactional}, starting a JTA transaction before the command
 * reaches the command bus.  Axon's {@code SimpleCommandBus} and the
 * {@code SubscribingEventProcessor} both run within that existing transaction.
 *
 * <p>The {@code QuartzDeadlineManager} fires in a separate Quartz worker
 * thread that has no ambient JTA transaction.  For that path, the
 * {@link #buildTransactionManager()} method begins a new JTA transaction
 * using {@link UserTransaction} so that saga state reads/writes and event
 * publication are atomic.
 *
 * <h2>Quartz configuration</h2>
 * The Quarkus Quartz extension is configured in {@code application.yaml}:
 * <pre>
 *   quarkus.quartz.store-type: ram     # RAMJobStore — jobs are in memory
 *   quarkus.quartz.start-mode: forced  # start immediately at boot
 * </pre>
 * Quarkus exposes the underlying {@code org.quartz.Scheduler} as a CDI bean,
 * which is injected here and passed to {@link QuartzDeadlineManager}.
 *
 * <h2>Startup ordering</h2>
 * Axon is initialised inside {@link #onStart(StartupEvent)} which fires after
 * CDI and the Quartz scheduler are fully started.  This prevents a race
 * condition where the deadline manager receives a {@code Scheduler} that has
 * not yet started.
 */
@Slf4j
@ApplicationScoped
public class AxonConfig {

    // -------------------------------------------------------------------------
    // Injected Quarkus / CDI resources
    // -------------------------------------------------------------------------

    /**
     * Transaction-scoped JPA EntityManager proxy.
     * The proxy automatically delegates to the EntityManager bound to the
     * currently active JTA transaction, so it is safe to capture once and
     * reuse across many transactions.
     */
    @PersistenceContext
    EntityManager entityManager;

    /**
     * JTA UserTransaction — used to begin/commit/rollback transactions in
     * the Quartz deadline worker threads (which have no ambient transaction).
     */
    @Inject
    UserTransaction userTransaction;

    /**
     * Standalone Quartz Scheduler managed directly by Axon (not by Quarkus).
     *
     * <p>We intentionally bypass the Quarkus-managed scheduler because Quarkus
     * only starts its Quartz scheduler if it finds {@code @Scheduled} annotated
     * methods.  Since this PoC has no such methods, we create a standalone
     * {@code StdSchedulerFactory}-based scheduler in {@link #onStart(StartupEvent)}
     * and stop it in {@link #onStop()}.
     *
     * <p>The {@code quarkus-quartz} Maven dependency still provides the Quartz
     * library classes; we just skip Quarkus's CDI/lifecycle wrapper.
     *
     * <p>Scheduler properties (RAMJobStore, thread count, etc.) are configured
     * programmatically in {@link #buildQuartzScheduler()}.
     */
    private Scheduler quartzScheduler;

    @Inject
    TaskProjection taskProjection;

    @Inject
    AuditTrailProjection auditTrailProjection;

    @Inject
    ExternalUserDirectoryClient externalUserDirectoryClient;

    // -------------------------------------------------------------------------
    // Internal state
    // -------------------------------------------------------------------------

    private Configuration axonConfiguration;

    // =========================================================================
    // Startup
    // =========================================================================

    /**
     * Initialises Axon after Quarkus (and Quartz) have fully started.
     *
     * <p>We observe {@link StartupEvent} rather than using {@code @PostConstruct}
     * because the Quartz scheduler must be running before we hand it to the
     * {@link QuartzDeadlineManager}.  {@link StartupEvent} is fired after all
     * CDI beans are initialised and the Quarkus scheduler extension has started.
     */
    void onStart(@Observes StartupEvent event) {
        log.info("Initialising Axon Framework...");

        // Start the standalone Quartz scheduler before configuring Axon.
        quartzScheduler = buildQuartzScheduler();

        EntityManagerProvider empProvider = () -> entityManager;
        TransactionManager txManager = buildTransactionManager();
        JacksonSerializer serializer = JacksonSerializer.builder().build();

        // -----------------------------------------------------------------
        // JPA Event Storage Engine (H2 via Hibernate ORM)
        // Axon's DomainEventEntry and SnapshotEventEntry tables are managed by
        // Hibernate/JPA metadata on startup with non-destructive schema updates.
        // -----------------------------------------------------------------
        // SQLErrorCodesResolver("H2") loads duplicate-key SQL error codes from
        // Axon's built-in SQLErrorCode.properties (H2.duplicateKeyCodes=23001,23505).
        // Without this, JpaEventStorageEngine cannot tell a constraint violation apart
        // from other exceptions and always throws EventStoreException instead of
        // AggregateStreamCreationException — preventing proper 409 mapping for
        // duplicate aggregate creation.
        // IMPORTANT: Pass the JacksonSerializer explicitly.
        // Without this, JpaEventStorageEngine defaults to XStream, which cannot
        // deserialize Java records (used for all event/command classes here).
        // Both the event serializer (domain events) and snapshot serializer must
        // use the same serializer instance for consistency.
        JpaEventStorageEngine storageEngine = JpaEventStorageEngine.builder()
                .entityManagerProvider(empProvider)
                .transactionManager(txManager)
                .persistenceExceptionResolver(new SQLErrorCodesResolver("H2"))
                .eventSerializer(serializer)
                .snapshotSerializer(serializer)
                .build();

        // -----------------------------------------------------------------
        // JPA Saga Store (H2 via Hibernate ORM)
        // Serialises TaskDeadlineSaga state (excluding transient fields) into
        // the SagaEntry / AssociationValueEntry tables.
        // -----------------------------------------------------------------
        JpaSagaStore sagaStore = JpaSagaStore.builder()
                .entityManagerProvider(empProvider)
                .serializer(serializer)
                .build();

        // -----------------------------------------------------------------
        // Build the Axon configuration.
        // Component callbacks (c -> ...) are evaluated lazily after the full
        // Configuration is built, which allows circular references to be
        // resolved safely (e.g., QuartzDeadlineManager needs the Configuration
        // to construct a ConfigurationScopeAwareProvider).
        // -----------------------------------------------------------------
        Configurer configurer = DefaultConfigurer.defaultConfiguration()
                .configureSerializer(c -> serializer)
                .configureEmbeddedEventStore(c -> storageEngine)
                .configureAggregate(TaskAggregate.class)
                // AxonResourceInjector: injects @Inject fields on
                // Saga instances directly from the Axon Configuration.
                // Previously used CdiResourceInjector (CDI BeanManager lookup), but that
                // failed at runtime because Quarkus's build-time CDI processing does not
                // always expose Axon interface types (DeadlineManager, EventBus) as
                // resolvable CDI beans before the configuration is fully started.
                // Using the Axon Configuration directly is more reliable.
                .configureResourceInjector(c -> new AxonResourceInjector(c, externalUserDirectoryClient))

                // Register the QuartzDeadlineManager via the dedicated API.
                // configureDeadlineManager receives the fully-built Configuration (c),
                // so the circular dependency (manager needs config, config registers manager)
                // is resolved lazily by the Configurer framework.
                // ConfigurationScopeAwareProvider uses the built Configuration to
                // locate the correct Saga repository when a Quartz job fires.
                .configureDeadlineManager(c ->
                        QuartzDeadlineManager.builder()
                                .scheduler(quartzScheduler)
                                .scopeAwareProvider(
                                        new org.axonframework.config.ConfigurationScopeAwareProvider(c))
                                .serializer(c.serializer())
                                .transactionManager(txManager)
                                .build()
                )

                .eventProcessing(ep -> ep
                        // SubscribingEventProcessor: delivers events synchronously
                        // in the same thread (and JTA transaction) as the command.
                        // This keeps the read model and audit trail consistent
                        // within each command's transaction boundary.
                        .usingSubscribingEventProcessors()

                        // JPA-backed Saga store (H2).
                        .registerSagaStore(c -> sagaStore)

                        // Projections (read model + audit trail).
                        .registerEventHandler(c -> taskProjection)
                        .registerEventHandler(c -> auditTrailProjection)

                        // Deadline Saga.
                        .registerSaga(TaskDeadlineSaga.class)
                        .registerSaga(UserProvisioningCompletionSaga.class)
                );

        axonConfiguration = configurer.buildConfiguration();

        // Register a BeanValidation interceptor on the command bus so that
        // commands are validated before reaching the aggregate.
        axonConfiguration.commandBus()
                .registerDispatchInterceptor(
                        new BeanValidationInterceptor<>());

        axonConfiguration.start();

        // Register @QueryHandler methods from the projection beans.
        // DefaultConfigurer's registerEventHandler() only wires @EventHandler methods.
        // @QueryHandler methods require explicit subscription via AnnotationQueryHandlerAdapter.
        new AnnotationQueryHandlerAdapter<>(taskProjection).subscribe(axonConfiguration.queryBus());
        new AnnotationQueryHandlerAdapter<>(auditTrailProjection).subscribe(axonConfiguration.queryBus());

        log.info("Axon Framework started successfully.");
    }

    // =========================================================================
    // Shutdown
    // =========================================================================

    @PreDestroy
    void onStop() {
        if (axonConfiguration != null) {
            log.info("Shutting down Axon Framework...");
            axonConfiguration.shutdown();
        }
        if (quartzScheduler != null) {
            try {
                quartzScheduler.shutdown(true); // wait for running jobs
                log.info("Quartz scheduler stopped.");
            } catch (Exception e) {
                log.warn("Error stopping Quartz scheduler", e);
            }
        }
    }

    // =========================================================================
    // CDI-exposed beans
    // =========================================================================

    /**
     * Exposes the Axon {@link CommandGateway} as a CDI bean for injection
     * into {@code TaskResource}.
     */
    @Produces
    @ApplicationScoped
    public CommandGateway commandGateway() {
        return axonConfiguration.commandGateway();
    }

    /**
     * Exposes the Axon {@link QueryGateway} as a CDI bean for injection
     * into {@code TaskResource}.
     */
    @Produces
    @ApplicationScoped
    public QueryGateway queryGateway() {
        return axonConfiguration.queryGateway();
    }

    /**
     * Exposes the {@link DeadlineManager} as a CDI bean so
     * {@link CdiResourceInjector} can inject it into {@link TaskDeadlineSaga}
     * instances.
     */
    @Produces
    @ApplicationScoped
    public DeadlineManager deadlineManager() {
        return axonConfiguration.getComponent(DeadlineManager.class);
    }

    /**
     * Exposes the Axon {@link EventBus} as a CDI bean so
     * {@link CdiResourceInjector} can inject it into {@link TaskDeadlineSaga}
     * for publishing {@code TaskDeadlineExceededEvent}.
     */
    @Produces
    @ApplicationScoped
    public EventBus eventBus() {
        return axonConfiguration.eventBus();
    }

    // =========================================================================
    // Quartz scheduler factory
    // =========================================================================

    /**
     * Creates and starts a standalone Quartz {@link Scheduler} with an in-memory
     * job store (RAMJobStore).
     *
     * <h3>Why standalone (not Quarkus-managed)?</h3>
     * Quarkus's Quartz extension only starts its managed scheduler when it detects
     * {@code @Scheduled} annotated methods at build time.  Because this PoC has none,
     * the Quarkus-managed scheduler would refuse to start, and its CDI
     * {@code org.quartz.Scheduler} producer would throw at injection time.
     *
     * <p>By creating the scheduler directly with {@link StdSchedulerFactory}, we
     * retain the full Quartz API while bypassing Quarkus's lifecycle guard.
     *
     * <h3>Configuration</h3>
     * <ul>
     *   <li>{@code RAMJobStore} — jobs are in memory; lost on restart (acceptable for PoC)</li>
     *   <li>5 worker threads — sufficient for the single deadline type in this PoC</li>
     *   <li>Named {@code "AxonDeadlineScheduler"} to distinguish from any other
     *       Quartz instances that may exist in the same JVM</li>
     * </ul>
     *
     * <p>For production, replace {@code RAMJobStore} with {@code JDBCJobStore} and
     * configure a JDBC data source to make deadlines persistent and clusterable.
     */
    private Scheduler buildQuartzScheduler() {
        try {
            var props = new Properties();
            props.setProperty("org.quartz.scheduler.instanceName", "AxonDeadlineScheduler");
            props.setProperty("org.quartz.scheduler.skipUpdateCheck", "true");
            props.setProperty("org.quartz.threadPool.class",
                    "org.quartz.simpl.SimpleThreadPool");
            props.setProperty("org.quartz.threadPool.threadCount", "5");
            props.setProperty("org.quartz.threadPool.threadPriority", "5");
            // RAMJobStore: jobs held in memory — no JDBC required.
            props.setProperty("org.quartz.jobStore.class",
                    "org.quartz.simpl.RAMJobStore");

            SchedulerFactory factory = new StdSchedulerFactory(props);
            Scheduler scheduler = factory.getScheduler();
            scheduler.start();
            log.info("Standalone Quartz scheduler started (RAMJobStore, 5 threads).");
            return scheduler;
        } catch (Exception e) {
            throw new RuntimeException("Failed to start standalone Quartz scheduler", e);
        }
    }

    // =========================================================================
    // Transaction manager bridge (Axon ↔ Quarkus JTA)
    // =========================================================================

    /**
     * Builds an Axon {@link TransactionManager} that bridges to Quarkus's
     * JTA implementation (Narayana).
     *
     * <h3>Behaviour</h3>
     * <ul>
     *   <li>If a JTA transaction is already active (e.g., started by
     *       {@code @Transactional} on the REST endpoint), this manager
     *       participates in it without beginning a new one.</li>
     *   <li>If no transaction is active (e.g., Quartz deadline worker thread),
     *       a new JTA transaction is begun and committed/rolled back by this
     *       manager.</li>
     * </ul>
     *
     * <p>This dual behaviour is necessary because Axon components are used in
     * both contexts (command handling within REST transactions, and deadline
     * handling in Quartz threads).
     */
    private TransactionManager buildTransactionManager() {
        return () -> {
            try {
                int txStatus = userTransaction.getStatus();
                boolean ownsTransaction = (txStatus == Status.STATUS_NO_TRANSACTION);

                if (ownsTransaction) {
                    userTransaction.begin();
                    log.debug("AxonTxManager: began new JTA transaction");
                }

                return new Transaction() {
                    @Override
                    public void commit() {
                        if (ownsTransaction) {
                            try {
                                userTransaction.commit();
                                log.debug("AxonTxManager: committed JTA transaction");
                            } catch (Exception e) {
                                throw new RuntimeException("AxonTxManager: commit failed", e);
                            }
                        }
                    }

                    @Override
                    public void rollback() {
                        if (ownsTransaction) {
                            try {
                                userTransaction.rollback();
                                log.debug("AxonTxManager: rolled back JTA transaction");
                            } catch (Exception e) {
                                throw new RuntimeException("AxonTxManager: rollback failed", e);
                            }
                        }
                    }
                };

            } catch (Exception e) {
                throw new RuntimeException("AxonTxManager: failed to manage JTA transaction", e);
            }
        };
    }
}
