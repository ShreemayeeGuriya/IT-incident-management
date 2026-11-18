package com.incident.backend.lifecycle.states;

import com.incident.backend.lifecycle.AbstractIncidentState;
import com.incident.backend.model.IncidentStatus;

import java.util.EnumSet;

/** Incident has been reported and awaits classification. */
public final class ReportedState extends AbstractIncidentState {
    public ReportedState() {
        super(IncidentStatus.REPORTED, EnumSet.of(IncidentStatus.CLASSIFIED));
    }
}
