package com.incident.backend.lifecycle.states;

import com.incident.backend.lifecycle.AbstractIncidentState;
import com.incident.backend.model.IncidentStatus;

import java.util.EnumSet;

/** Incident category and affected service are known; priority is next. */
public final class ClassifiedState extends AbstractIncidentState {
    public ClassifiedState() {
        super(IncidentStatus.CLASSIFIED, EnumSet.of(IncidentStatus.PRIORITIZED));
    }
}
