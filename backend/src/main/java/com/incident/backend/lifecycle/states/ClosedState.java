package com.incident.backend.lifecycle.states;

import com.incident.backend.lifecycle.AbstractIncidentState;
import com.incident.backend.model.IncidentStatus;

import java.util.EnumSet;

/** Terminal state: no further transitions are allowed. */
public final class ClosedState extends AbstractIncidentState {
    public ClosedState() {
        super(IncidentStatus.CLOSED, EnumSet.noneOf(IncidentStatus.class));
    }
}
