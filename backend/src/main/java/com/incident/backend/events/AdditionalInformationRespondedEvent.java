package com.incident.backend.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AdditionalInformationRespondedEvent(UUID eventId, Integer incidentId, Integer actorUserId,
        Instant occurredAt, Integer requestId, Integer requestingTechnicianUserId) implements IncidentAuditEvent {
    @Override public String action() { return "ADDITIONAL_INFORMATION_RESPONDED"; }
    @Override public Map<String, String> oldValues() { return Map.of("responseStatus", "OPEN"); }
    @Override public Map<String, String> newValues() { return Map.of("informationRequestId", requestId.toString(),
            "responseStatus", "RESPONDED"); }
    @Override public String description() { return "The reporter responded to information request #" + requestId; }
}
