package eu.poc.taskmanagement.config;

import eu.poc.taskmanagement.model.TaskAggregate;
import eu.poc.taskmanagement.projection.audittrail.AuditTrailProjection;
import eu.poc.taskmanagement.projection.tasks.TaskProjection;
import eu.poc.taskmanagement.saga.TaskDeadlineProcessManager;
import eu.poc.taskmanagement.saga.UserProvisioningProcessManager;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.conversion.jackson.JacksonConverter;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.eventsourcing.eventstore.jpa.AggregateBasedJpaEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.SQLErrorCodesResolver;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.core.unitofwork.transaction.TransactionManager;
import org.axonframework.messaging.core.unitofwork.transaction.jpa.JpaTransactionalExecutorProvider;
import org.axonframework.messaging.eventhandling.conversion.DelegatingEventConverter;
import org.axonframework.messaging.queryhandling.configuration.QueryHandlingModule;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Axon Framework 5 CDI configuration for Quarkus.
 *
 * <h2>Overview</h2>
 * This class manually wires the Axon Framework 5 components using the
 * {@link EventSourcingConfigurer} API — the CDI equivalent of what Spring Boot
 * auto-configuration would do.  Quarkus uses CDI (not Spring), so every
 * component is declared here and the resulting {@link AxonConfiguration} is held
 * as an application-scoped bean.  The {@link CommandGateway} and
 * {@link QueryGateway} are exposed as {@code @Produces} beans so the rest of the
 * application can inject them directly.
 *
 * <h2>Component map</h2>
 * <pre>
 *  EventSourcedEntityModule            — auto-detects TaskAggregate (@EventSourcedEntity)
 *  AggregateBasedJpaEventStorageEngine — stores domain events in H2 via JPA
 *  DelegatingEventConverter/Jackson    — serialises events (Jackson, records supported)
 *  QueryHandlingModule                 — auto-detects @QueryHandler projections
 *  Subscribing event processor         — synchronous event delivery to projections
 *                                        and process managers
 * </pre>
 *
 * <h2>Sagas / deadlines</h2>
 * Axon 5.3.1 ships no saga, deadline or scheduling modules.  The former
 * {@code TaskDeadlineSaga} and {@code UserProvisioningCompletionSaga} are
 * reimplemented as plain CDI process-manager beans
 * ({@link TaskDeadlineProcessManager}, {@link UserProvisioningProcessManager})
 * that are registered here as event-handling components on the subscribing
 * processor.  They use a {@code DeadlineScheduler}
 * ({@link PersistentDeadlineScheduler}, backed by a durable {@code scheduled_job}
 * table) instead of Quartz for timed call-backs, so schedules survive a restart
 * and are claimed by exactly one node in a cluster.
 *
 * <h2>Transaction model</h2>
 * The REST layer annotates command-dispatching methods with
 * {@code @Transactional}, so command handling and the subscribing projection
 * handlers run within that JTA transaction.  The
 * {@link JpaTransactionalExecutorProvider} manages its own {@code EntityManager}
 * (created from the {@link EntityManagerFactory}) for event-store reads/writes.
 *
 * <h2>Startup ordering</h2>
 * Axon is initialised inside {@link #onStart(StartupEvent)}, which fires after
 * all CDI beans (including the projections and process managers) are ready.
 */
@Slf4j
@ApplicationScoped
public class AxonConfig {

    // -------------------------------------------------------------------------
    // Injected Quarkus / CDI resources
    // -------------------------------------------------------------------------

    /**
     * JPA {@link EntityManagerFactory}.  Handed to the
     * {@link JpaTransactionalExecutorProvider}, which creates and manages its
     * own {@code EntityManager} for the event store.
     */
    @Inject
    EntityManagerFactory entityManagerFactory;

    /**
     * Transaction-scoped JPA {@link EntityManager} proxy.  Delegates to the
     * {@code EntityManager} bound to the active JTA transaction, so it is safe
     * to capture once and reuse.  Used by the {@link QuarkusJtaTransactionManager}
     * to expose the JTA-joined {@code EntityManager} to the Axon event store.
     */
    @PersistenceContext
    EntityManager entityManager;

    @Inject
    TaskProjection taskProjection;

    @Inject
    AuditTrailProjection auditTrailProjection;

    @Inject
    TaskDeadlineProcessManager taskDeadlineProcessManager;

    @Inject
    UserProvisioningProcessManager userProvisioningProcessManager;

    /**
     * Database product name used by Axon's {@link SQLErrorCodesResolver} to load
     * the correct SQL error codes for duplicate-key detection.
     *
     * <p>Supported values (must match Axon's SQLErrorCode.properties):
     * <ul>
     *   <li>{@code H2} — H2 in-memory database</li>
     *   <li>{@code Microsoft SQL Server} — MSSQL</li>
     *   <li>{@code PostgreSQL}, {@code MySQL}, {@code Oracle}, etc.</li>
     * </ul>
     */
    @ConfigProperty(name = "axon.event-store.sql-error-codes.database-product-name", defaultValue = "H2")
    String databaseProductName;

    // -------------------------------------------------------------------------
    // Internal state
    // -------------------------------------------------------------------------

    private AxonConfiguration axonConfiguration;

    // =========================================================================
    // Startup
    // =========================================================================

    void onStart(@Observes StartupEvent event) {
        log.info("Initialising Axon Framework 5...");

        EntityManagerProvider entityManagerProvider = () -> entityManager;

        EventSourcingConfigurer configurer = EventSourcingConfigurer.create()
                // ------------------------------------------------------------
                // Transaction manager — integrates the Axon event store with
                // Quarkus-managed JTA.  Registering a TransactionManager
                // component makes Axon's default TransactionalUnitOfWorkFactory
                // attach the JTA-joined EntityManager executor to each unit of
                // work, which the JPA event store requires.
                // ------------------------------------------------------------
                .componentRegistry(cr -> cr.registerComponent(
                        TransactionManager.class,
                        c -> new QuarkusJtaTransactionManager(entityManagerProvider)))

                // ------------------------------------------------------------
                // Event-sourced entity — TaskAggregate is annotated with
                // @EventSourcedEntity, so it is auto-detected (command handlers,
                // @EntityCreator and @EventSourcingHandler methods scanned).
                // ------------------------------------------------------------
                .registerEntity(EventSourcedEntityModule.autodetected(String.class, TaskAggregate.class))

                // ------------------------------------------------------------
                // JPA event storage engine (H2 via Hibernate ORM).
                // Aggregate-based engine: exactly one tag per event = the
                // aggregate id (each event carries a single @EventTag("taskId")).
                //
                // A DelegatingEventConverter backed by JacksonConverter is used
                // so Java records (all events) serialise correctly; the default
                // XStream-free Jackson path handles records natively.
                //
                // SQLErrorCodesResolver loads duplicate-key SQL error codes for
                // the configured database product so a duplicate aggregate
                // creation surfaces as a proper conflict (HTTP 409) rather than a
                // generic event-store exception.
                // ------------------------------------------------------------
                .registerEventStorageEngine(c -> new AggregateBasedJpaEventStorageEngine(
                        new JpaTransactionalExecutorProvider(entityManagerFactory),
                        new DelegatingEventConverter(new JacksonConverter()),
                        cfg -> cfg.persistenceExceptionResolver(new SQLErrorCodesResolver(databaseProductName))))

                // ------------------------------------------------------------
                // Query handling — @QueryHandler methods on the projections are
                // auto-detected and registered on the query bus.
                // ------------------------------------------------------------
                .registerQueryHandlingModule(
                        QueryHandlingModule.named("task-queries")
                                .queryHandlers()
                                .autodetectedQueryHandlingComponent(c -> taskProjection)
                                .autodetectedQueryHandlingComponent(c -> auditTrailProjection))

                // ------------------------------------------------------------
                // Event processing — a single subscribing processor delivers
                // events synchronously (in the command's thread/transaction) to
                // the projections and the process managers.  @EventHandler
                // methods on each component are auto-detected.
                // ------------------------------------------------------------
                .messaging(m -> m.eventProcessing(ep -> ep.subscribing(sub ->
                        sub.processor("task-events", phase -> phase
                                .eventHandlingComponents(comps -> comps
                                        .autodetected("taskProjection", c -> taskProjection)
                                        .autodetected("auditTrailProjection", c -> auditTrailProjection)
                                        .autodetected("taskDeadlineProcessManager", c -> taskDeadlineProcessManager)
                                        .autodetected("userProvisioningProcessManager", c -> userProvisioningProcessManager))
                                .notCustomized()))));

        axonConfiguration = configurer.build();
        axonConfiguration.start();

        log.info("Axon Framework 5 started successfully.");
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
    }

    // =========================================================================
    // CDI-exposed beans
    // =========================================================================

    /**
     * Exposes the Axon {@link CommandGateway} as a CDI bean for injection
     * into the REST layer, application services and process managers.
     */
    @Produces
    @ApplicationScoped
    public CommandGateway commandGateway() {
        return axonConfiguration.getComponent(CommandGateway.class);
    }

    /**
     * Exposes the Axon {@link QueryGateway} as a CDI bean for injection
     * into the query application service.
     */
    @Produces
    @ApplicationScoped
    public QueryGateway queryGateway() {
        return axonConfiguration.getComponent(QueryGateway.class);
    }
}
