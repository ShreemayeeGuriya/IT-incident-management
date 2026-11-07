package com.incident.backend.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record IncidentUpdatedEvent(UUID eventId, Integer incidentId, Integer actorUserId,
        Instant occurredAt, Map<String, String> oldValues, Map<String, String> newValues,
        String description) implements IncidentAuditEvent {
    public IncidentUpdatedEvent {
        oldValues = Map.copyOf(oldValues);
        newValues = Map.copyOf(newValues);
    }
    @Override public String action() { return "INCIDENT_UPDATED"; }
}
