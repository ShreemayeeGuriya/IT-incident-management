package com.incident.backend.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record IncidentStatusChangedEvent(UUID eventId, Integer incidentId, Integer actorUserId,
        Instant occurredAt, String oldStatus, String newStatus) implements IncidentAuditEvent {
    @Override public String action() {
        return switch (newStatus) {
            case "RESOLVED" -> "INCIDENT_RESOLVED";
            case "CLOSED" -> "INCIDENT_CLOSED";
            default -> "INCIDENT_STATUS_CHANGED";
        };
    }
    @Override public Map<String, String> oldValues() { return Map.of("status", oldStatus); }
    @Override public Map<String, String> newValues() { return Map.of("status", newStatus); }
    @Override public String description() { return "Incident status changed from " + oldStatus + " to " + newStatus; }
}
