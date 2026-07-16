package eu.poc.taskmanagement.api;

import eu.poc.taskmanagement.model.TaskStatus;
import eu.poc.taskmanagement.projection.audittrail.AuditTrailEntry;
import eu.poc.taskmanagement.projection.audittrail.query.GetAuditTrailByTaskQuery;
import eu.poc.taskmanagement.projection.tasks.TaskView;
import eu.poc.taskmanagement.projection.tasks.query.GetAllTasksQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;

import java.time.Duration;
import java.util.List;

/**
 * Test helper that encapsulates all interaction with query/read stores
 * and event database. Allows tests to speak in functional terms rather
 * than infrastructure-specific SQL queries.
 */
@ApplicationScoped
class QueryStore {

    @Inject
    QueryGateway queryGateway;

    @PersistenceContext
    EntityManager entityManager;

    /**
     * Find all tasks with the given status.
     */
    public List<TaskView> findTasksByStatus(TaskStatus status) throws Exception {
        return query(
                new GetAllTasksQuery(status, null, null, 0, 50),
                TaskView.class);
    }

    /**
     * Get the audit trail (all domain events) for a task.
     */
    public List<AuditTrailEntry> getAuditTrail(String taskId) throws Exception {
        return query(
                new GetAuditTrailByTaskQuery(taskId),
                AuditTrailEntry.class);
    }

    /**
     * Count domain events published for a specific aggregate.
     */
    public long countDomainEvents(String aggregateId) {
        Number result = (Number) entityManager.createNativeQuery(
                "select count(*) from domainevententry where aggregateidentifier = ?1")
                .setParameter(1, aggregateId)
                .getSingleResult();
        return result.longValue();
    }

    /**
     * Count active saga associations for a task (indicates if saga is still running).
     */
    public long countActiveSagaAssociations(String taskId) {
        Number result = (Number) entityManager.createNativeQuery(
                "select count(*) from associationvalueentry where associationvalue = ?1")
                .setParameter(1, taskId)
                .getSingleResult();
        return result.longValue();
    }

    /**
     * Poll the audit trail until a specific event type appears, or timeout.
     * Useful for testing async/eventual-consistency scenarios.
     */
    public void waitForEvent(String taskId, String eventType, Duration timeout) throws Exception {
        long timeoutAt = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < timeoutAt) {
            List<AuditTrailEntry> audit = getAuditTrail(taskId);
            if (audit.stream().anyMatch(entry -> eventType.equals(entry.eventType))) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Event '" + eventType + "' not found in audit trail for taskId=" + taskId
                + " within timeout " + timeout);
    }

    /**
     * Execute a query against the query store.
     */
    private <T> List<T> query(Object queryMessage, Class<T> responseType) throws Exception {
        return queryGateway.query(queryMessage, ResponseTypes.multipleInstancesOf(responseType)).get();
    }
}
