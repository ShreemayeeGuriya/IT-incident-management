package com.incident.backend.lifecycle.states;

import com.incident.backend.lifecycle.AbstractIncidentState;
import com.incident.backend.model.IncidentStatus;

import java.util.EnumSet;

/** The assigned technician has acknowledged the incident and is working on it. */
public final class InvestigatingState extends AbstractIncidentState {
    public InvestigatingState() {
        super(IncidentStatus.INVESTIGATING, EnumSet.of(IncidentStatus.RESOLVED));
    }
}
