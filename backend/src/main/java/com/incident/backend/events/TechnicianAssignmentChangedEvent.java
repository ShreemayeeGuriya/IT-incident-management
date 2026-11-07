package com.incident.backend.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TechnicianAssignmentChangedEvent(UUID eventId, Integer incidentId, Integer actorUserId,
        Instant occurredAt, Integer oldTechnicianId, Integer newTechnicianId,
        String description) implements IncidentAuditEvent {
    @Override public String action() { return "TECHNICIAN_ASSIGNMENT_CHANGED"; }
    @Override public Map<String, String> oldValues() {
        return oldTechnicianId == null ? Map.of() : Map.of("technicianId", oldTechnicianId.toString());
    }
    @Override public Map<String, String> newValues() {
        return newTechnicianId == null ? Map.of() : Map.of("technicianId", newTechnicianId.toString());
    }
}
