package com.incident.backend.priority;

import com.incident.backend.model.IncidentStatus;

import java.util.EnumSet;
import java.util.Set;

/** Shared status lock for automatic factor recalculation and explicit priority recalculation. */
public final class PriorityRecalculationPolicy {
    private static final Set<IncidentStatus> LOCKED = EnumSet.of(
            IncidentStatus.RESOLVED, IncidentStatus.VERIFIED, IncidentStatus.CLOSED);

    private PriorityRecalculationPolicy() { }

    public static boolean isLocked(IncidentStatus status) {
        return LOCKED.contains(status);
    }
}
