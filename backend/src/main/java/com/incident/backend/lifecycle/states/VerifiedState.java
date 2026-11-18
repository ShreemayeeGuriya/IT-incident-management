package com.incident.backend.lifecycle.states;

import com.incident.backend.lifecycle.AbstractIncidentState;
import com.incident.backend.model.IncidentStatus;

import java.util.EnumSet;

/** The reporter or an administrator has confirmed the resolution. */
public final class VerifiedState extends AbstractIncidentState {
    public VerifiedState() {
        super(IncidentStatus.VERIFIED, EnumSet.of(IncidentStatus.CLOSED));
    }
}
