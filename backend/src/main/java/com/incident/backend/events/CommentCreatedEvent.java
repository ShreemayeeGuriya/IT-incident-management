package com.incident.backend.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CommentCreatedEvent(UUID eventId, Integer incidentId, Integer actorUserId,
        Instant occurredAt, Integer commentId) implements IncidentAuditEvent {
    @Override public String action() { return "COMMENT_CREATED"; }
    @Override public Map<String, String> oldValues() { return Map.of(); }
    @Override public Map<String, String> newValues() { return Map.of("commentId", commentId.toString()); }
    @Override public String description() { return "A comment was added to the incident"; }
}
