package com.incident.backend.service;

import com.incident.backend.events.IncidentEscalatedEvent;
import com.incident.backend.events.SlaObjectiveTransitionEvent;
import com.incident.backend.model.SlaObjective;
import com.incident.backend.model.SlaObjectiveStatus;
import com.incident.backend.repository.IncidentEscalationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentEscalationServiceTest {
    @Mock IncidentEscalationRepository escalationRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks IncidentEscalationService escalationService;

    @Test
    void responseBreachCreatesLevelOneEscalationEvent() {
        when(escalationRepository.insertIfAbsent(eq(41), eq(1), eq("RESPONSE"), any(Instant.class)))
                .thenReturn(1);

        escalationService.onSlaObjectiveTransition(transition(SlaObjective.RESPONSE));

        IncidentEscalatedEvent escalated = publishedEvent();
        assertEquals(1, escalated.escalationLevel());
        assertEquals(SlaObjective.RESPONSE, escalated.objective());
        verify(escalationRepository).insertIfAbsent(eq(41), eq(1), eq("RESPONSE"), any(Instant.class));
    }

    @Test
    void resolutionBreachIndependentlyCreatesLevelTwoEscalationEvent() {
        when(escalationRepository.insertIfAbsent(eq(41), eq(2), eq("RESOLUTION"), any(Instant.class)))
                .thenReturn(1);

        escalationService.onSlaObjectiveTransition(transition(SlaObjective.RESOLUTION));

        IncidentEscalatedEvent escalated = publishedEvent();
        assertEquals(2, escalated.escalationLevel());
        assertEquals(SlaObjective.RESOLUTION, escalated.objective());
        verify(escalationRepository).insertIfAbsent(eq(41), eq(2), eq("RESOLUTION"), any(Instant.class));
    }

    @Test
    void duplicateLevelInsertDoesNotPublishAnotherEscalationEvent() {
        when(escalationRepository.insertIfAbsent(eq(41), eq(1), eq("RESPONSE"), any(Instant.class)))
                .thenReturn(0);

        escalationService.onSlaObjectiveTransition(transition(SlaObjective.RESPONSE));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void nonBreachTransitionDoesNotCreateAnEscalation() {
        escalationService.onSlaObjectiveTransition(new SlaObjectiveTransitionEvent(UUID.randomUUID(), 41, null,
                Instant.parse("2026-01-01T00:00:00Z"), SlaObjective.RESPONSE,
                SlaObjectiveStatus.WITHIN_SLA, SlaObjectiveStatus.APPROACHING_BREACH,
                Instant.parse("2026-01-01T00:05:00Z")));

        verifyNoInteractions(escalationRepository, eventPublisher);
    }

    private SlaObjectiveTransitionEvent transition(SlaObjective objective) {
        return new SlaObjectiveTransitionEvent(UUID.randomUUID(), 41, null,
                Instant.parse("2026-01-01T00:05:00Z"), objective,
                SlaObjectiveStatus.APPROACHING_BREACH, SlaObjectiveStatus.BREACHED,
                Instant.parse("2026-01-01T00:05:00Z"));
    }

    private IncidentEscalatedEvent publishedEvent() {
        ArgumentCaptor<IncidentEscalatedEvent> captor = ArgumentCaptor.forClass(IncidentEscalatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }
}
