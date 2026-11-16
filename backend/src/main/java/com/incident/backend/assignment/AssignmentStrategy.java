package com.incident.backend.assignment;

import com.incident.backend.model.Incident;
import com.incident.backend.strategy.NamedStrategy;

import java.util.List;
import java.util.Optional;

/**
 * Chooses a technician for an incident (Strategy pattern). Administrators can switch the active
 * strategy at runtime without changing any other part of the application.
 */
public interface AssignmentStrategy extends NamedStrategy {

    /**
     * @param incident the incident to assign
     * @param eligible technicians that already pass {@link AssignmentRules} (available, under their
     *                 workload limit), ordered by id; never empty
     * @return the chosen technician, or empty if none suits this strategy
     */
    Optional<TechnicianCandidate> choose(Incident incident, List<TechnicianCandidate> eligible);

    /** Called after the chosen technician was actually assigned (not after a recommendation). */
    default void onAssigned(TechnicianCandidate chosen) { }

    /** Why {@link #choose} found nobody, used in the error message. */
    default String noMatchReason(Incident incident) {
        return "no eligible technician matched";
    }
}
