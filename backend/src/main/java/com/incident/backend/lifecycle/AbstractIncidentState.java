package com.incident.backend.lifecycle;

import com.incident.backend.model.IncidentStatus;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Shared implementation: subclasses only declare their status and the statuses that may follow it. */
public abstract class AbstractIncidentState implements IncidentState {
    private final IncidentStatus status;
    private final Set<IncidentStatus> allowedNext;

    protected AbstractIncidentState(IncidentStatus status, Set<IncidentStatus> allowedNext) {
        this.status = status;
        this.allowedNext = allowedNext.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(allowedNext));
    }

    @Override
    public final IncidentStatus status() {
        return status;
    }

    @Override
    public final Set<IncidentStatus> allowedNext() {
        return allowedNext;
    }

    @Override
    public String toString() {
        return status.name();
    }
}
