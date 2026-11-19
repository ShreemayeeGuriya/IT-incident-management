package com.incident.backend.service;

import com.incident.backend.dto.IncidentCreateRequest;
import com.incident.backend.events.IncidentCreatedEvent;
import com.incident.backend.exception.BadRequestException;
import com.incident.backend.model.*;
import com.incident.backend.priority.ImpactUrgencyMatrixPriorityStrategy;
import com.incident.backend.priority.PriorityCalculator;
import com.incident.backend.priority.WeightedScorePriorityStrategy;
import com.incident.backend.repository.*;
import com.incident.backend.dto.IncidentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentServicePriorityCreationTest {
    private static final Instant CREATED_AT = Instant.parse("2026-01-02T03:04:05Z");

    @Mock IncidentRepository incidentRepository;
    @Mock UserRepository userRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock AffectedServiceRepository serviceRepository;
    @Mock SlaService slaService;
    @Mock IncidentAccess access;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock AuditLogRepository auditLogRepository;
    @Mock IncidentWorkflowService workflowService;

    private final PriorityCalculator priorityCalculator = new PriorityCalculator(
            List.of(new WeightedScorePriorityStrategy(), new ImpactUrgencyMatrixPriorityStrategy()),
            WeightedScorePriorityStrategy.NAME);
    private final Category category = mock(Category.class);
    private final AffectedService service = mock(AffectedService.class);
    private final SlaPolicy policy = mock(SlaPolicy.class);
    private final User reporter = mock(User.class);
    private IncidentService incidentService;

    @BeforeEach
    void setUp() {
        incidentService = new IncidentService(incidentRepository, userRepository, categoryRepository,
                serviceRepository, slaService, priorityCalculator, access, eventPublisher, auditLogRepository,
                workflowService);

        when(categoryRepository.findById(1)).thenReturn(Optional.of(category));
        when(serviceRepository.findById(2)).thenReturn(Optional.of(service));
        when(category.getId()).thenReturn(1);
        when(service.getId()).thenReturn(2);
    }

    @Test
    void creationStoresPriorityCalculatedFromTheFactors() {
        var response = create(Impact.HIGH, Urgency.HIGH, BusinessCriticality.HIGH, Priority.P1);

        assertEquals(Priority.P1, response.priority());
        assertEquals(IncidentStatus.PRIORITIZED, response.status());
        assertEquals(Priority.P1, firstSavedIncident().getPriority());
        assertEquals(CREATED_AT.plusSeconds(60), response.responseDeadline());
        assertEquals(CREATED_AT.plusSeconds(600), response.resolutionDeadline());
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        IncidentCreatedEvent event = (IncidentCreatedEvent) eventCaptor.getValue();
        assertEquals("P1", event.newValues().get("priority"));
        verify(workflowService).advanceCreationToPrioritized(41L, reporter);
    }

    @Test
    void omittedPriorityIsCalculatedAndStored() {
        var response = create(Impact.MEDIUM, Urgency.MEDIUM, BusinessCriticality.MEDIUM, null);

        assertEquals(Priority.P3, response.priority());
        assertEquals(Priority.P3, firstSavedIncident().getPriority());
    }

    @Test
    void matchingSuppliedPriorityIsAccepted() {
        var response = create(Impact.HIGH, Urgency.HIGH, BusinessCriticality.HIGH, Priority.P1);

        assertEquals(Priority.P1, response.priority());
        verify(slaService).resolvePolicy(Priority.P1, null);
    }

    @Test
    void mismatchingSuppliedPriorityIsRejectedBeforePersistence() {
        IncidentCreateRequest request = request(Impact.HIGH, Urgency.HIGH,
                BusinessCriticality.HIGH, Priority.P4);

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> incidentService.create(request, reporter));

        assertEquals("Supplied priority P4 does not match calculated priority P1", error.getMessage());
        verifyNoInteractions(slaService, incidentRepository, eventPublisher);
    }

    @Test
    void slaPolicyResolutionUsesTheCalculatedPriorityFromTheActiveStrategy() {
        priorityCalculator.strategies().select(ImpactUrgencyMatrixPriorityStrategy.NAME);

        var response = create(Impact.LOW, Urgency.LOW, BusinessCriticality.HIGH, null);

        assertEquals(Priority.P3, response.priority());
        verify(slaService).resolvePolicy(Priority.P3, null);
        assertEquals(Priority.P3, firstSavedIncident().getPriority());
    }

    private com.incident.backend.dto.IncidentResponse create(Impact impact, Urgency urgency,
                                                               BusinessCriticality criticality,
                                                               Priority priority) {
        stubSuccessfulCreate();
        return incidentService.create(request(impact, urgency, criticality, priority), reporter);
    }

    private void stubSuccessfulCreate() {
        when(userRepository.getReferenceById(7)).thenReturn(reporter);
        when(reporter.getId()).thenReturn(7);
        when(policy.getId()).thenReturn(9);
        when(slaService.resolvePolicy(any(Priority.class), isNull())).thenReturn(policy);
        when(slaService.calculateDeadlines(any(Instant.class), same(policy)))
                .thenReturn(new SlaService.Deadlines(CREATED_AT.plusSeconds(60), CREATED_AT.plusSeconds(600)));
        when(incidentRepository.saveAndFlush(any(Incident.class))).thenAnswer(invocation -> {
            Incident incident = invocation.getArgument(0);
            if (incident.getId() == null) ReflectionTestUtils.setField(incident, "id", 41);
            if (incident.getCreatedAt() == null) ReflectionTestUtils.setField(incident, "createdAt", CREATED_AT);
            return incident;
        });
        when(workflowService.advanceCreationToPrioritized(41L, reporter)).thenAnswer(invocation -> {
            Incident incident = firstSavedIncident();
            incident.setStatus(IncidentStatus.PRIORITIZED);
            return IncidentResponse.from(incident);
        });
    }

    private IncidentCreateRequest request(Impact impact, Urgency urgency,
                                          BusinessCriticality criticality, Priority priority) {
        return new IncidentCreateRequest("Create test incident", 1, 2, null,
                impact, urgency, criticality, priority);
    }

    private Incident firstSavedIncident() {
        ArgumentCaptor<Incident> captor = ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository, atLeastOnce()).saveAndFlush(captor.capture());
        return captor.getAllValues().get(0);
    }
}
