package com.incident.backend.lifecycle.states;

import com.incident.backend.exception.InvalidStateTransitionException;
import com.incident.backend.lifecycle.AbstractIncidentState;
import com.incident.backend.model.Incident;
import com.incident.backend.model.IncidentStatus;

import java.util.EnumSet;

/** A technician owns the incident. Moving back to PRIORITIZED releases the technician. */
public final class AssignedState extends AbstractIncidentState {
    public AssignedState() {
        super(IncidentStatus.ASSIGNED, EnumSet.of(IncidentStatus.INVESTIGATING, IncidentStatus.PRIORITIZED));
    }

    /** An incident can only be ASSIGNED when a technician is actually set. */
    @Override
    public void validateEntry(Incident incident) {
        if (incident.getAssignedTechnician() == null) {
            throw InvalidStateTransitionException.guardFailed(incident.getStatus(), status(),
                    "a technician must be assigned first");
        }
    }
}
