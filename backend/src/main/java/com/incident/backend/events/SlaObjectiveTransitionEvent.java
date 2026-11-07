package com.incident.backend.events;

import com.incident.backend.model.SlaObjective;
import com.incident.backend.model.SlaObjectiveStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Immutable system-generated snapshot of one meaningful SLA objective transition. */
public record SlaObjectiveTransitionEvent(UUID eventId, Integer incidentId, Integer actorUserId,
        Instant occurredAt, SlaObjective objective, SlaObjectiveStatus oldStatus,
        SlaObjectiveStatus newStatus, Instant deadline) implements IncidentAuditEvent {

    @Override
    public String action() {
        return "SLA_" + objective.name() + "_" + newStatus.name();
    }

    @Override
    public Map<String, String> oldValues() {
        return Map.of("objective", objective.name(), "status", oldStatus.name());
    }

    @Override
    public Map<String, String> newValues() {
        return Map.of("objective", objective.name(), "status", newStatus.name(),
                "deadline", deadline.toString());
    }

    @Override
    public String description() {
        return objective.name().toLowerCase() + " SLA changed from " + oldStatus
                + " to " + newStatus + "; deadline=" + deadline;
    }
}
