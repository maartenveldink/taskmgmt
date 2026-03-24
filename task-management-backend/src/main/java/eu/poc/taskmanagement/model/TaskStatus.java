package eu.poc.taskmanagement.model;

/**
 * All possible lifecycle states of a Task aggregate.
 *
 * <p>Valid transitions:
 * <pre>
 *   CREATED ──► ASSIGNED ──► IN_PROGRESS ──► DONE        (terminal)
 *                │                        └──► CANCELLED  (terminal)
 *                └──► REJECTED                            (terminal)
 *   CREATED ──► REJECTED                                  (terminal)
 *   CREATED ──► CANCELLED                                 (terminal)
 * </pre>
 *
 * <p>DONE, CANCELLED, and REJECTED are terminal: no further commands are
 * accepted on an aggregate that has reached one of these states.
 */
public enum TaskStatus {
    CREATED,
    ASSIGNED,
    IN_PROGRESS,
    DONE,
    CANCELLED,
    REJECTED;

    /** Returns true if this status is a terminal state (no further transitions allowed). */
    public boolean isTerminal() {
        return this == DONE || this == CANCELLED || this == REJECTED;
    }
}
