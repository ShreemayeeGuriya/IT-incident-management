package com.incident.backend.lifecycle.states;

import com.incident.backend.exception.InvalidStateTransitionException;
import com.incident.backend.lifecycle.AbstractIncidentState;
import com.incident.backend.model.Incident;
import com.incident.backend.model.IncidentStatus;

import java.util.EnumSet;

/** A fix has been applied. Moving back to INVESTIGATING reopens the incident if the fix did not work. */
public final class ResolvedState extends AbstractIncidentState {
    public ResolvedState() {
        super(IncidentStatus.RESOLVED, EnumSet.of(IncidentStatus.VERIFIED, IncidentStatus.INVESTIGATING));
    }

    /** An incident can only be RESOLVED once resolution details have been recorded. */
    @Override
    public void validateEntry(Incident incident) {
        String details = incident.getResolutionDetails();
        if (details == null || details.isBlank()) {
            throw InvalidStateTransitionException.guardFailed(incident.getStatus(), status(),
                    "resolution details must be provided first");
        }
    }
}
