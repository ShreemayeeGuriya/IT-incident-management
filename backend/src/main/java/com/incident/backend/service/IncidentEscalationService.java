package com.incident.backend.service;

import com.incident.backend.events.IncidentEscalatedEvent;
import com.incident.backend.events.SlaObjectiveTransitionEvent;
import com.incident.backend.model.SlaObjective;
import com.incident.backend.model.SlaObjectiveStatus;
import com.incident.backend.repository.IncidentEscalationRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Turns an SLA breach transition into one idempotent escalation per objective level. */
@Service
public class IncidentEscalationService {
    private final IncidentEscalationRepository escalationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public IncidentEscalationService(IncidentEscalationRepository escalationRepository,
                                     ApplicationEventPublisher eventPublisher) {
        this.escalationRepository = escalationRepository;
        this.eventPublisher = eventPublisher;
    }

    /** Runs in the SLA evaluation transaction; an absent transaction is a programming error. */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onSlaObjectiveTransition(SlaObjectiveTransitionEvent event) {
        if (event.newStatus() != SlaObjectiveStatus.BREACHED) return;

        int level = switch (event.objective()) {
            case RESPONSE -> 1;
            case RESOLUTION -> 2;
        };
        int inserted = escalationRepository.insertIfAbsent(event.incidentId(), level,
                event.objective().name(), event.occurredAt());
        if (inserted == 0) return;
        if (inserted != 1) {
            throw new IllegalStateException("Unexpected escalation insert count: " + inserted);
        }

        eventPublisher.publishEvent(new IncidentEscalatedEvent(UUID.randomUUID(), event.incidentId(), null,
                event.occurredAt(), level, event.objective()));
    }
}
