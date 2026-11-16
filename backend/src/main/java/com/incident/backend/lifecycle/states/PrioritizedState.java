package com.incident.backend.lifecycle.states;

import com.incident.backend.lifecycle.AbstractIncidentState;
import com.incident.backend.model.IncidentStatus;

import java.util.EnumSet;

/** Priority and SLA are set; the incident awaits a technician. */
public final class PrioritizedState extends AbstractIncidentState {
    public PrioritizedState() {
        super(IncidentStatus.PRIORITIZED, EnumSet.of(IncidentStatus.ASSIGNED));
    }
}
