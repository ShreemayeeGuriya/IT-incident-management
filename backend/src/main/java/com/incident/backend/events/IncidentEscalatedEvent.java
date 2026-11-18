package com.incident.backend.events;

import com.incident.backend.model.SlaObjective;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** System-generated audit snapshot for one persisted escalation level. */
public record IncidentEscalatedEvent(UUID eventId, Integer incidentId, Integer actorUserId,
        Instant occurredAt, int escalationLevel, SlaObjective objective) implements IncidentAuditEvent {

    @Override public String action() { return "INCIDENT_ESCALATED"; }
    @Override public Map<String, String> oldValues() { return Map.of(); }
    @Override public Map<String, String> newValues() {
        return Map.of("escalationLevel", Integer.toString(escalationLevel), "objective", objective.name());
    }
    @Override public String description() {
        return "Incident escalated to level " + escalationLevel + " after "
                + objective.name().toLowerCase() + " SLA breach";
    }
}
