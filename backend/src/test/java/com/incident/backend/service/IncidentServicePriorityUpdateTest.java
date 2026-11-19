package com.incident.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incident.backend.dto.IncidentUpdateRequest;
import com.incident.backend.events.IncidentAuditEvent;
import com.incident.backend.events.IncidentUpdatedEvent;
import com.incident.backend.exception.BadRequestException;
import com.incident.backend.exception.IncidentNotEligibleException;
import com.incident.backend.model.AuditLog;
import com.incident.backend.model.*;
import com.incident.backend.priority.*;
import com.incident.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static com.incident.backend.model.Priority.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentServicePriorityUpdateTest {
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

    private final PriorityCalculator calculator = new PriorityCalculator(
            List.of(new WeightedScorePriorityStrategy(), new ImpactUrgencyMatrixPriorityStrategy()),
            WeightedScorePriorityStrategy.NAME);
    private IncidentService service;
    private Incident incident;
    private User admin;
    private SlaPolicy p1;
    private SlaPolicy p2;
    private SlaPolicy p3;
    private SlaPolicy p4;

    @BeforeEach
    void setUp() {
        service = new IncidentService(incidentRepository, userRepository, categoryRepository, serviceRepository,
                slaService, calculator, access, eventPublisher, auditLogRepository, workflowService);
        incident = new Incident();
        incident.setDescription("Original");
        incident.setImpact(Impact.MEDIUM);
        incident.setUrgency(Urgency.MEDIUM);
        incident.setBusinessCriticality(BusinessCriticality.MEDIUM);
        incident.setPriority(Priority.P3);
        incident.setStatus(IncidentStatus.REPORTED);
        User reporter = new User();
        ReflectionTestUtils.setField(reporter, "id", 8);
        reporter.setRole(Role.EMPLOYEE);
        incident.setReporter(reporter);
        Category category = new Category();
        ReflectionTestUtils.setField(category, "id", 10);
        ReflectionTestUtils.setField(category, "name", "Network");
        incident.setCategory(category);
        AffectedService affectedService = new AffectedService();
        ReflectionTestUtils.setField(affectedService, "id", 11);
        ReflectionTestUtils.setField(affectedService, "name", "Test service");
        incident.setService(affectedService);
        ReflectionTestUtils.setField(incident, "id", 1);
        ReflectionTestUtils.setField(incident, "createdAt", CREATED_AT);
        admin = new User();
        admin.setRole(Role.ADMIN);
        ReflectionTestUtils.setField(admin, "id", 7);

        p1 = policy(1, Priority.P1, 5, 30);
        p2 = policy(2, Priority.P2, 10, 60);
        p3 = policy(3, Priority.P3, 20, 120);
        p4 = policy(4, Priority.P4, 30, 240);
        incident.setSlaPolicy(p3);
        incident.setResponseDeadline(CREATED_AT.plusSeconds(1200));
        incident.setResolutionDeadline(CREATED_AT.plusSeconds(7200));
        when(access.requireViewableForUpdate(1L, admin)).thenReturn(incident);
        lenient().when(incidentRepository.saveAndFlush(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(slaService.resolvePolicy(any(Priority.class), nullable(Integer.class))).thenAnswer(inv -> {
            Priority priority = inv.getArgument(0);
            Integer explicitId = inv.getArgument(1);
            SlaPolicy selected = explicitId == null ? policy(priority) : policy(explicitId);
            if (selected.getPriority() != priority) {
                throw new BadRequestException("SLA policy priority must match incident priority");
            }
            return selected;
        });
        lenient().when(slaService.calculateDeadlines(any(Instant.class), any(SlaPolicy.class))).thenAnswer(inv -> {
            Instant created = inv.getArgument(0);
            SlaPolicy policy = inv.getArgument(1);
            return new SlaService.Deadlines(created.plusSeconds(policy.getResponseTimeMinutes() * 60L),
                    created.plusSeconds(policy.getResolutionTimeMinutes() * 60L));
        });
    }

    @Test
    void changingImpactAloneRecalculatesPriority() {
        update(request(null, Impact.HIGH, null, null, null, null));
        assertEquals(P2, incident.getPriority());
        assertEquals(Impact.HIGH, incident.getImpact());
    }

    @Test
    void changingUrgencyAloneRecalculatesPriority() {
        update(request(null, null, Urgency.HIGH, null, null, null));
        assertEquals(P2, incident.getPriority());
        assertEquals(Urgency.HIGH, incident.getUrgency());
    }

    @Test
    void changingBusinessCriticalityAloneRecalculatesPriority() {
        update(request(null, null, null, BusinessCriticality.HIGH, null, null));
        assertEquals(P2, incident.getPriority());
        assertEquals(BusinessCriticality.HIGH, incident.getBusinessCriticality());
    }

    @Test
    void changingMultipleFactorsCalculatesFromTheResultingCombination() {
        update(request(null, Impact.HIGH, Urgency.HIGH, BusinessCriticality.LOW, null, null));
        assertEquals(P2, incident.getPriority());
    }

    @Test
    void noFactorChangesDoNotRecalculateOrChangePriority() {
        update(request("Updated description", null, null, null, null, null));
        assertEquals(P3, incident.getPriority());
        verify(slaService, never()).resolvePolicy(any(), any());
    }

    @Test
    void factorChangeThatKeepsCalculatedPriorityDoesNotAuditAPriorityChange() {
        update(request(null, Impact.LOW, null, null, null, null)); // LOW*MEDIUM*MEDIUM remains P3.
        assertEquals(P3, incident.getPriority());
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        IncidentUpdatedEvent event = (IncidentUpdatedEvent) captor.getValue();
        assertEquals("LOW", event.newValues().get("impact"));
        assertFalse(event.oldValues().containsKey("priority"));
        assertFalse(event.newValues().containsKey("priority"));
    }

    @Test
    void matchingSuppliedPriorityIsAcceptedWithFactorChange() {
        update(request(null, Impact.HIGH, Urgency.HIGH, BusinessCriticality.HIGH, P1, null));
        assertEquals(P1, incident.getPriority());
    }

    @Test
    void mismatchingSuppliedPriorityIsRejectedBeforeMutation() {
        BadRequestException error = assertThrows(BadRequestException.class,
                () -> update(request(null, Impact.HIGH, Urgency.HIGH, BusinessCriticality.HIGH, P4, null)));
        assertEquals("Supplied priority P4 does not match calculated priority P1", error.getMessage());
        assertEquals(Impact.MEDIUM, incident.getImpact());
        assertEquals(P3, incident.getPriority());
        verify(incidentRepository, never()).saveAndFlush(any());
    }

    @Test
    void priorityOnlyUpdateMustMatchCurrentFactorAssessment() {
        BadRequestException error = assertThrows(BadRequestException.class,
                () -> update(request(null, null, null, null, P1, null)));
        assertEquals("Supplied priority P1 does not match calculated priority P3", error.getMessage());
        update(request(null, null, null, null, P3, null));
        assertEquals(P3, incident.getPriority());
    }

    @Test
    void priorityChangeSelectsCompatibleSlaAndRecalculatesDeadlines() {
        update(request(null, Impact.HIGH, Urgency.HIGH, BusinessCriticality.HIGH, null, null));
        assertEquals(P1, incident.getPriority());
        assertSame(p1, incident.getSlaPolicy());
        assertEquals(CREATED_AT.plusSeconds(300), incident.getResponseDeadline());
        assertEquals(CREATED_AT.plusSeconds(1800), incident.getResolutionDeadline());
        verify(slaService).resolvePolicy(P1, null);
    }

    @Test
    void explicitIncompatibleSlaPolicyIsRejectedBeforeMutation() {
        BadRequestException error = assertThrows(BadRequestException.class,
                () -> update(request(null, Impact.HIGH, Urgency.HIGH, BusinessCriticality.HIGH, null, 3)));
        assertEquals("SLA policy priority must match incident priority", error.getMessage());
        assertEquals(Impact.MEDIUM, incident.getImpact());
        assertEquals(P3, incident.getPriority());
        verify(incidentRepository, never()).saveAndFlush(any());
    }

    @Test
    void calculatedPriorityChangeIsIncludedInUpdateAuditEvent() {
        Instant before = Instant.now();
        update(request(null, Impact.HIGH, Urgency.HIGH, BusinessCriticality.HIGH, null, null));
        Instant after = Instant.now();
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        IncidentUpdatedEvent event = (IncidentUpdatedEvent) captor.getValue();
        assertEquals("INCIDENT_UPDATED", event.action());
        assertEquals(1, event.incidentId());
        assertEquals(7, event.actorUserId());
        assertFalse(event.occurredAt().isBefore(before));
        assertFalse(event.occurredAt().isAfter(after));
        assertEquals("P3", event.oldValues().get("priority"));
        assertEquals("P1", event.newValues().get("priority"));
        assertTrue(event.description().contains("priority"));

        when(incidentRepository.getReferenceById(1)).thenReturn(incident);
        when(userRepository.getReferenceById(7)).thenReturn(admin);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            new AuditLogService(auditLogRepository, incidentRepository, userRepository, access,
                    new ObjectMapper()).record((IncidentAuditEvent) event);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).saveAndFlush(auditCaptor.capture());
        AuditLog audit = auditCaptor.getValue();
        assertSame(incident, audit.getIncident());
        assertSame(admin, audit.getActor());
        assertEquals("INCIDENT_UPDATED", audit.getAction());
        assertEquals(event.occurredAt(), audit.getTimestamp());
        assertEquals("P3", readJson(audit.getOldValue(), "priority"));
        assertEquals("P1", readJson(audit.getNewValue(), "priority"));
    }

    @ParameterizedTest
    @EnumSource(value = IncidentStatus.class, names = {"RESOLVED", "VERIFIED", "CLOSED"})
    void classificationFactorsCannotChangeAfterResolution(IncidentStatus status) {
        incident.setStatus(status);
        assertThrows(IncidentNotEligibleException.class,
                () -> update(request(null, Impact.HIGH, null, null, null, null)));
        assertEquals(Impact.MEDIUM, incident.getImpact());
        verify(incidentRepository, never()).saveAndFlush(any());
    }

    @Test
    void unrelatedUpdateRemainsAllowedAfterResolution() {
        incident.setStatus(IncidentStatus.CLOSED);
        update(request("Closed incident note", null, null, null, null, null));
        assertEquals("Closed incident note", incident.getDescription());
        assertEquals(IncidentStatus.CLOSED, incident.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = IncidentStatus.class, names = {"RESOLVED", "VERIFIED", "CLOSED"})
    void explicitPriorityRecalculationUsesTheSameTerminalStatusLock(IncidentStatus status) {
        incident.setStatus(status);
        PriorityService priorityService = new PriorityService(calculator, access, service);
        assertThrows(IncidentNotEligibleException.class, () -> priorityService.recalculate(1L, admin));
        verify(incidentRepository, never()).saveAndFlush(any());
    }

    @Test
    void priorityServiceExplicitRecalculationRemainsCompatible() {
        incident.setPriority(P4);
        incident.setImpact(Impact.HIGH);
        incident.setUrgency(Urgency.HIGH);
        incident.setBusinessCriticality(BusinessCriticality.HIGH);
        incident.setSlaPolicy(p4);
        PriorityService priorityService = new PriorityService(calculator, access, service);

        PriorityService.Recalculation result = priorityService.recalculate(1L, admin);

        assertTrue(result.changed());
        assertEquals(P1, result.assessment().priority());
        assertEquals(P1, incident.getPriority());
        assertSame(p1, incident.getSlaPolicy());
    }

    private void update(IncidentUpdateRequest request) {
        service.update(1L, request, admin);
    }

    private IncidentUpdateRequest request(String description, Impact impact, Urgency urgency,
                                          BusinessCriticality criticality, Priority priority, Integer slaPolicyId) {
        return new IncidentUpdateRequest(description, null, null, slaPolicyId, impact, urgency,
                criticality, priority, null, null, null);
    }

    private SlaPolicy policy(Priority priority) {
        return switch (priority) { case P1 -> p1; case P2 -> p2; case P3 -> p3; case P4 -> p4; };
    }

    private SlaPolicy policy(int id) {
        return switch (id) { case 1 -> p1; case 2 -> p2; case 3 -> p3; case 4 -> p4;
            default -> throw new IllegalArgumentException("Unknown test policy " + id); };
    }

    private static SlaPolicy policy(int id, Priority priority, int response, int resolution) {
        SlaPolicy policy = new SlaPolicy();
        ReflectionTestUtils.setField(policy, "id", id);
        ReflectionTestUtils.setField(policy, "priority", priority);
        ReflectionTestUtils.setField(policy, "responseTimeMinutes", response);
        ReflectionTestUtils.setField(policy, "resolutionTimeMinutes", resolution);
        return policy;
    }

    private static String readJson(String json, String field) {
        try {
            return new ObjectMapper().readTree(json).path(field).asText();
        } catch (Exception ex) {
            throw new AssertionError("Invalid audit JSON", ex);
        }
    }
}
