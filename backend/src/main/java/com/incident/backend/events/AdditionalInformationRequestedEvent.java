package com.incident.backend.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AdditionalInformationRequestedEvent(UUID eventId, Integer incidentId, Integer actorUserId,
        Instant occurredAt, Integer requestId, Integer reporterUserId) implements IncidentAuditEvent {
    @Override public String action() { return "ADDITIONAL_INFORMATION_REQUESTED"; }
    @Override public Map<String, String> oldValues() { return Map.of(); }
    @Override public Map<String, String> newValues() { return Map.of("informationRequestId", requestId.toString(),
            "responseStatus", "OPEN"); }
    @Override public String description() { return "Additional information was requested (request #" + requestId + ")"; }
}
