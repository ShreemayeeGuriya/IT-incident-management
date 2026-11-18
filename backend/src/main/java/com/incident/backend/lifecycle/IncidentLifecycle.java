package com.incident.backend.lifecycle;

import com.incident.backend.exception.InvalidStateTransitionException;
import com.incident.backend.model.Incident;
import com.incident.backend.model.IncidentStatus;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * The single entry point for changing an incident's status.
 *
 * <p>It asks the incident's current {@link IncidentState} whether the move is legal and whether the
 * incident meets the target state's entry conditions, then applies the change. Nothing else in the
 * application should call {@code Incident.setStatus} directly.</p>
 */
@Component
public class IncidentLifecycle {
    private final IncidentStateFactory stateFactory;

    public IncidentLifecycle(IncidentStateFactory stateFactory) {
        this.stateFactory = stateFactory;
    }

    public IncidentState currentState(Incident incident) {
        return stateFactory.stateOf(incident.getStatus());
    }

    public Set<IncidentStatus> allowedNext(Incident incident) {
        return currentState(incident).allowedNext();
    }

    public boolean canTransition(Incident incident, IncidentStatus target) {
        return currentState(incident).canTransitionTo(target);
    }

    /**
     * Moves the incident to {@code target}.
     *
     * @return the previous status
     * @throws InvalidStateTransitionException if the move is not allowed or a guard condition fails
     */
    public IncidentStatus transition(Incident incident, IncidentStatus target) {
        if (target == null) {
            throw new IllegalArgumentException("Target status must not be null");
        }
        IncidentState current = currentState(incident);
        if (!current.canTransitionTo(target)) {
            throw InvalidStateTransitionException.notAllowed(current.status(), target, current.allowedNext());
        }
        stateFactory.stateOf(target).validateEntry(incident);
        IncidentStatus previous = incident.getStatus();
        incident.setStatus(target);
        return previous;
    }
}
