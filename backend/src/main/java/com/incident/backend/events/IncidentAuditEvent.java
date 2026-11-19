package com.incident.backend.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Immutable, entity-free snapshot published inside the business transaction. */
public sealed interface IncidentAuditEvent permits IncidentCreatedEvent, IncidentUpdatedEvent,
        TechnicianAssignmentChangedEvent, IncidentStatusChangedEvent, CommentCreatedEvent,
        SlaObjectiveTransitionEvent {
    UUID eventId();
    Integer incidentId();
    Integer actorUserId();
    Instant occurredAt();
    String action();
    Map<String, String> oldValues();
    Map<String, String> newValues();
    String description();
}
