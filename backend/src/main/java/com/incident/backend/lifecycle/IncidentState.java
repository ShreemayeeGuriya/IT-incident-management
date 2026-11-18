package com.incident.backend.lifecycle;

import com.incident.backend.model.Incident;
import com.incident.backend.model.IncidentStatus;

import java.util.Set;

/**
 * One stage of the incident lifecycle (State pattern).
 *
 * <p>Each implementation knows which stages may follow it and which
 * conditions an incident must meet before it may enter the stage.</p>
 */
public interface IncidentState {

    /** The persisted status this state represents. */
    IncidentStatus status();

    /** Statuses an incident may move to from this state. */
    Set<IncidentStatus> allowedNext();

    /** True if moving from this state to {@code target} is a legal lifecycle step. */
    default boolean canTransitionTo(IncidentStatus target) {
        return allowedNext().contains(target);
    }

    /** True if no further transitions are possible. */
    default boolean isTerminal() {
        return allowedNext().isEmpty();
    }

    /**
     * Guard conditions checked before an incident enters this state.
     * Throws {@link com.incident.backend.exception.InvalidStateTransitionException} when not met.
     */
    default void validateEntry(Incident incident) { }
}
