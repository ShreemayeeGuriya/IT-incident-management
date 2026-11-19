package com.incident.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.incident.backend.dto.IncidentCreateRequest;
import com.incident.backend.dto.IncidentUpdateRequest;
import com.incident.backend.exception.BadRequestException;
import com.incident.backend.model.*;
import com.incident.backend.repository.*;
import com.incident.backend.security.JwtService;
import com.incident.backend.service.IncidentService;
import com.incident.backend.service.SlaService;
import com.incident.backend.service.SlaMonitoringService;
import com.incident.backend.service.SlaMonitorScheduler;
import com.incident.backend.service.UserService;
import com.incident.backend.service.IncidentWorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("postgres-it")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestDatabaseGuard.class)
@AutoConfigureMockMvc
@Transactional
@EnabledIfSystemProperty(named = "postgres.integration", matches = "true")
class PostgresSlaDeadlineIT {
    @Autowired IncidentService incidentService;
    @Autowired UserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired AffectedServiceRepository serviceRepository;
    @Autowired SlaPolicyRepository slaPolicyRepository;
    @Autowired IncidentRepository incidentRepository;
    @Autowired SlaMonitoringService slaMonitoringService;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired SlaMonitorScheduler slaMonitorScheduler;
    @Autowired UserService userService;
    @Autowired IncidentWorkflowService incidentWorkflowService;
    @Autowired TechnicianRepository technicianRepository;
    @Autowired ObjectMapper objectMapper;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @MockitoSpyBean SlaService slaService;

    private User reporter() {
        User user = new User();
        user.setName("SLA deadline integration test");
        user.setEmail("sla-deadline-" + UUID.randomUUID() + "@example.test");
        user.setPasswordHash("test-hash");
        user.setRole(Role.ADMIN);
        return userRepository.saveAndFlush(user);
    }

    private IncidentCreateRequest createRequest(Priority priority, Integer policyId) {
        Category category = categoryRepository.findAll(Sort.by("id")).get(0);
        AffectedService service = serviceRepository.findAll(Sort.by("id")).get(0);
        Impact impact = switch (priority) {
            case P1, P2 -> Impact.HIGH;
            case P3 -> Impact.MEDIUM;
            case P4 -> Impact.LOW;
        };
        Urgency urgency = impact == Impact.HIGH ? Urgency.HIGH : impact == Impact.MEDIUM
                ? Urgency.MEDIUM : Urgency.LOW;
        BusinessCriticality criticality = priority == Priority.P2 ? BusinessCriticality.LOW
                : impact == Impact.HIGH ? BusinessCriticality.HIGH : impact == Impact.MEDIUM
                ? BusinessCriticality.MEDIUM : BusinessCriticality.LOW;
        return new IncidentCreateRequest("SLA deadline integration test", category.getId(), service.getId(),
                policyId, impact, urgency, criticality, priority);
    }

    private IncidentUpdateRequest updateRequest(Integer policyId, Priority priority, String description) {
        Impact impact = priority == null ? null : switch (priority) {
            case P1, P2 -> Impact.HIGH;
            case P3 -> Impact.MEDIUM;
            case P4 -> Impact.LOW;
        };
        Urgency urgency = impact == null ? null : impact == Impact.HIGH ? Urgency.HIGH
                : impact == Impact.MEDIUM ? Urgency.MEDIUM : Urgency.LOW;
        BusinessCriticality criticality = priority == null ? null : priority == Priority.P2
                ? BusinessCriticality.LOW : impact == Impact.HIGH ? BusinessCriticality.HIGH
                : impact == Impact.MEDIUM ? BusinessCriticality.MEDIUM : BusinessCriticality.LOW;
        return new IncidentUpdateRequest(description, null, null, policyId, impact, urgency, criticality,
                priority, null, null, null);
    }

    @Test
    @Rollback
    void creationAutomaticallySelectsPriorityPolicyAndAuditsDeadlines() throws Exception {
        User reporter = reporter();
        for (Priority priority : Priority.values()) {
            SlaPolicy expectedPolicy = slaPolicyRepository.findByPriority(priority).orElseThrow();
            var created = incidentService.create(createRequest(priority, null), reporter);

            assertEquals(expectedPolicy.getId().longValue(), created.slaPolicy().id());
            assertEquals(created.createdAt().plus(Duration.ofMinutes(expectedPolicy.getResponseTimeMinutes())),
                    created.responseDeadline());
            assertEquals(created.createdAt().plus(Duration.ofMinutes(expectedPolicy.getResolutionTimeMinutes())),
                    created.resolutionDeadline());

            AuditLog audit = auditLogRepository
                    .findByIncidentIdOrderByTimestampAscIdAsc(Math.toIntExact(created.id())).get(0);
            JsonNode snapshot = objectMapper.readTree(audit.getNewValue());
            assertEquals(expectedPolicy.getId().toString(), snapshot.get("slaPolicyId").asText());
            assertEquals(created.responseDeadline().toString(), snapshot.get("responseDeadline").asText());
            assertEquals(created.resolutionDeadline().toString(), snapshot.get("resolutionDeadline").asText());
        }
    }

    @Test
    @Rollback
    void explicitlySuppliedMatchingPolicyIsAccepted() {
        User reporter = reporter();
        SlaPolicy policy = slaPolicyRepository.findByPriority(Priority.P2).orElseThrow();

        var created = incidentService.create(createRequest(Priority.P2, policy.getId()), reporter);

        assertEquals(policy.getId().longValue(), created.slaPolicy().id());
    }

    @Test
    @Rollback
    void explicitlySuppliedMismatchedPolicyIsRejected() {
        User reporter = reporter();
        SlaPolicy p1 = slaPolicyRepository.findByPriority(Priority.P1).orElseThrow();

        assertThrows(BadRequestException.class,
                () -> incidentService.create(createRequest(Priority.P2, p1.getId()), reporter));
    }

    @Test
    @Rollback
    void priorityChangeAutomaticallySelectsPolicyAndRecalculatesFromOriginalCreatedAt() throws Exception {
        User reporter = reporter();
        var created = incidentService.create(createRequest(Priority.P1, null), reporter);
        SlaPolicy p2 = slaPolicyRepository.findByPriority(Priority.P2).orElseThrow();
        Instant originalCreatedAt = created.createdAt();

        var updated = incidentService.update(created.id(), updateRequest(null, Priority.P2, null), reporter);

        assertEquals(p2.getId().longValue(), updated.slaPolicy().id());
        assertEquals(originalCreatedAt.plus(Duration.ofMinutes(p2.getResponseTimeMinutes())),
                updated.responseDeadline());
        assertEquals(originalCreatedAt.plus(Duration.ofMinutes(p2.getResolutionTimeMinutes())),
                updated.resolutionDeadline());
        assertEquals(originalCreatedAt, updated.createdAt());

        AuditLog updateAudit = auditLogRepository
                .findByIncidentIdOrderByTimestampAscIdAsc(Math.toIntExact(created.id())).get(3);
        JsonNode oldValues = objectMapper.readTree(updateAudit.getOldValue());
        JsonNode newValues = objectMapper.readTree(updateAudit.getNewValue());
        assertEquals(created.slaPolicy().id().toString(), oldValues.get("slaPolicyId").asText());
        assertEquals(p2.getId().toString(), newValues.get("slaPolicyId").asText());
        assertEquals(created.responseDeadline().toString(), oldValues.get("responseDeadline").asText());
        assertEquals(updated.responseDeadline().toString(), newValues.get("responseDeadline").asText());
        assertEquals(created.resolutionDeadline().toString(), oldValues.get("resolutionDeadline").asText());
        assertEquals(updated.resolutionDeadline().toString(), newValues.get("resolutionDeadline").asText());
    }

    @Test
    @Rollback
    void priorityChangeAcceptsExplicitMatchingPolicyAndUsesItsDurations() {
        User reporter = reporter();
        var created = incidentService.create(createRequest(Priority.P1, null), reporter);
        SlaPolicy p2 = slaPolicyRepository.findByPriority(Priority.P2).orElseThrow();

        var updated = incidentService.update(created.id(),
                updateRequest(p2.getId(), Priority.P2, null), reporter);

        assertEquals(p2.getId().longValue(), updated.slaPolicy().id());
        assertEquals(created.createdAt().plus(Duration.ofMinutes(p2.getResponseTimeMinutes())),
                updated.responseDeadline());
        assertEquals(created.createdAt().plus(Duration.ofMinutes(p2.getResolutionTimeMinutes())),
                updated.resolutionDeadline());
    }

    @Test
    @Rollback
    void explicitPolicyAssignmentRecalculatesDeadlinesWhenExistingPolicyIsAbsent() {
        User reporter = reporter();
        var created = incidentService.create(createRequest(Priority.P2, null), reporter);
        SlaPolicy p2 = slaPolicyRepository.findByPriority(Priority.P2).orElseThrow();
        Incident incident = incidentRepository.findById(Math.toIntExact(created.id())).orElseThrow();
        incident.setSlaPolicy(null);
        incident.setResponseDeadline(null);
        incident.setResolutionDeadline(null);
        incidentRepository.saveAndFlush(incident);

        var updated = incidentService.update(created.id(),
                updateRequest(p2.getId(), null, null), reporter);

        assertEquals(p2.getId().longValue(), updated.slaPolicy().id());
        assertEquals(created.createdAt().plus(Duration.ofMinutes(p2.getResponseTimeMinutes())),
                updated.responseDeadline());
        assertEquals(created.createdAt().plus(Duration.ofMinutes(p2.getResolutionTimeMinutes())),
                updated.resolutionDeadline());
    }

    @Test
    @Rollback
    void unchangedPriorityAndPolicyPreserveDeadlinesWithoutRecalculation() {
        User reporter = reporter();
        var created = incidentService.create(createRequest(Priority.P1, null), reporter);
        clearInvocations(slaService);

        var updated = incidentService.update(created.id(),
                updateRequest(null, null, "Description-only update"), reporter);

        assertEquals(created.responseDeadline(), updated.responseDeadline());
        assertEquals(created.resolutionDeadline(), updated.resolutionDeadline());
        verify(slaService, never()).calculateDeadlines(any(Instant.class), any(SlaPolicy.class));
    }

    @Test
    @Rollback
    void monitoringEvaluatesObjectivesIndependentlyAndCandidateQueryUsesThresholdCutoffs() throws Exception {
        User reporter = reporter();
        var created = incidentService.create(createRequest(Priority.P1, null), reporter);
        SlaPolicy policy = slaPolicyRepository.findByPriority(Priority.P1).orElseThrow();
        Instant start = created.createdAt();
        var pageable = PageRequest.of(0, 100);

        var initial = slaMonitoringService.evaluateIncident(Math.toIntExact(created.id()), start.minusSeconds(1))
                .orElseThrow();
        assertEquals(SlaObjectiveStatus.WITHIN_SLA, initial.responseStatus());
        assertEquals(SlaObjectiveStatus.WITHIN_SLA, initial.resolutionStatus());
        assertEquals(SlaStatus.WITHIN_SLA, initial.summaryStatus());
        assertApiSlaStatuses(created.id(), reporter, SlaObjectiveStatus.WITHIN_SLA,
                SlaObjectiveStatus.WITHIN_SLA);
        assertFalse(slaMonitoringService.evaluateIncident(Math.toIntExact(created.id()), start.minusSeconds(1))
                .orElseThrow().changed());

        Instant beforeThreshold = start;
        assertFalse(incidentRepository.findSlaCandidateIds(policy.getId(), SlaObjectiveStatus.NOT_STARTED,
                        SlaObjectiveStatus.WITHIN_SLA, SlaObjectiveStatus.APPROACHING_BREACH,
                        slaService.approachCandidateCutoff(beforeThreshold, policy.getResponseTimeMinutes(), 0.8),
                        slaService.approachCandidateCutoff(beforeThreshold, policy.getResolutionTimeMinutes(), 0.8),
                        beforeThreshold, pageable)
                .contains(Math.toIntExact(created.id())));

        Instant responseApproachingAt = start.plus(Duration.ofMinutes(4));
        assertTrue(incidentRepository.findSlaCandidateIds(policy.getId(), SlaObjectiveStatus.NOT_STARTED,
                        SlaObjectiveStatus.WITHIN_SLA, SlaObjectiveStatus.APPROACHING_BREACH,
                        slaService.approachCandidateCutoff(responseApproachingAt,
                                policy.getResponseTimeMinutes(), 0.8),
                        slaService.approachCandidateCutoff(responseApproachingAt,
                                policy.getResolutionTimeMinutes(), 0.8),
                        responseApproachingAt, pageable)
                .contains(Math.toIntExact(created.id())));

        var responseApproaching = slaMonitoringService
                .evaluateIncident(Math.toIntExact(created.id()), responseApproachingAt).orElseThrow();
        assertEquals(SlaObjectiveStatus.APPROACHING_BREACH, responseApproaching.responseStatus());
        assertEquals(SlaObjectiveStatus.WITHIN_SLA, responseApproaching.resolutionStatus());
        assertApiSlaStatuses(created.id(), reporter, SlaObjectiveStatus.APPROACHING_BREACH,
                SlaObjectiveStatus.WITHIN_SLA);

        var responseBreached = slaMonitoringService.evaluateIncident(Math.toIntExact(created.id()),
                start.plus(Duration.ofMinutes(5))).orElseThrow();
        assertEquals(SlaObjectiveStatus.BREACHED, responseBreached.responseStatus());
        assertEquals(SlaObjectiveStatus.WITHIN_SLA, responseBreached.resolutionStatus());
        assertEquals(SlaStatus.RESPONSE_BREACHED, responseBreached.summaryStatus());
        assertApiSlaStatuses(created.id(), reporter, SlaObjectiveStatus.BREACHED,
                SlaObjectiveStatus.WITHIN_SLA);

        var resolutionApproaching = slaMonitoringService.evaluateIncident(Math.toIntExact(created.id()),
                start.plus(Duration.ofMinutes(24))).orElseThrow();
        assertEquals(SlaObjectiveStatus.BREACHED, resolutionApproaching.responseStatus());
        assertEquals(SlaObjectiveStatus.APPROACHING_BREACH, resolutionApproaching.resolutionStatus());
        assertApiSlaStatuses(created.id(), reporter, SlaObjectiveStatus.BREACHED,
                SlaObjectiveStatus.APPROACHING_BREACH);

        var bothBreached = slaMonitoringService.evaluateIncident(Math.toIntExact(created.id()),
                start.plus(Duration.ofMinutes(30))).orElseThrow();
        assertEquals(SlaObjectiveStatus.BREACHED, bothBreached.responseStatus());
        assertEquals(SlaObjectiveStatus.BREACHED, bothBreached.resolutionStatus());
        assertEquals(SlaStatus.BREACHED, bothBreached.summaryStatus());
        assertApiSlaStatuses(created.id(), reporter, SlaObjectiveStatus.BREACHED,
                SlaObjectiveStatus.BREACHED);
    }

    private void assertApiSlaStatuses(Long incidentId, User reporter,
                                      SlaObjectiveStatus responseStatus,
                                      SlaObjectiveStatus resolutionStatus) throws Exception {
        mvc.perform(get("/api/incidents/{id}", incidentId)
                        .header("Authorization", "Bearer " + jwtService.generateToken(reporter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseSlaStatus").value(responseStatus.name()))
                .andExpect(jsonPath("$.resolutionSlaStatus").value(resolutionStatus.name()));
    }

    @Test
    @Rollback
    void schedulerAuditsApproachingTransitionAndNotifiesOnlyOnBreachOnce() {
        User reporter = reporter();
        var created = incidentService.create(createRequest(Priority.P1, null), reporter);
        Integer incidentId = Math.toIntExact(created.id());
        Instant start = created.createdAt();
        User technicianUser = new User();
        technicianUser.setName("SLA assigned technician");
        technicianUser.setEmail("sla-tech-" + UUID.randomUUID() + "@example.test");
        technicianUser.setPasswordHash("test-hash");
        technicianUser.setRole(Role.EMPLOYEE);
        technicianUser = userRepository.saveAndFlush(technicianUser);
        Integer technicianUserId = technicianUser.getId();
        userService.updateRole(technicianUser.getId().longValue(), Role.TECHNICIAN, reporter);
        Integer technicianId = technicianRepository.findByUserId(technicianUser.getId()).orElseThrow().getId();
        incidentWorkflowService.assign(created.id(), technicianId.longValue(), reporter);

        slaMonitorScheduler.runMonitoringCycle(start.plus(Duration.ofMinutes(4)));
        var afterApproaching = auditLogRepository.findByIncidentIdOrderByTimestampAscIdAsc(incidentId);
        assertTrue(afterApproaching.stream().anyMatch(a -> a.getAction().equals("SLA_RESPONSE_APPROACHING_BREACH")));
        assertFalse(notificationRepository.findAll().stream().anyMatch(n -> incidentId.equals(
                n.getIncident() == null ? null : n.getIncident().getId())
                && n.getNotificationType().equals("SLA_RESPONSE_APPROACHING_BREACH")));

        slaMonitorScheduler.runMonitoringCycle(start.plus(Duration.ofMinutes(5)));
        var afterBreach = auditLogRepository.findByIncidentIdOrderByTimestampAscIdAsc(incidentId);
        assertEquals(1, afterBreach.stream().filter(a -> a.getAction().equals("SLA_RESPONSE_BREACHED")).count());
        assertEquals(1, notificationRepository.findAll().stream().filter(n -> incidentId.equals(
                n.getIncident() == null ? null : n.getIncident().getId())
                && n.getNotificationType().equals("SLA_RESPONSE_BREACHED")
                && n.getUser().getId().equals(reporter.getId())).count());
        assertEquals(1, notificationRepository.findAll().stream().filter(n -> incidentId.equals(
                n.getIncident() == null ? null : n.getIncident().getId())
                && n.getNotificationType().equals("SLA_RESPONSE_BREACHED")
                && n.getUser().getId().equals(technicianUserId)).count());

        slaMonitorScheduler.runMonitoringCycle(start.plus(Duration.ofMinutes(6)));
        var afterRepeat = auditLogRepository.findByIncidentIdOrderByTimestampAscIdAsc(incidentId);
        assertEquals(1, afterRepeat.stream().filter(a -> a.getAction().equals("SLA_RESPONSE_BREACHED")).count());
        assertEquals(1, notificationRepository.findAll().stream().filter(n -> incidentId.equals(
                n.getIncident() == null ? null : n.getIncident().getId())
                && n.getNotificationType().equals("SLA_RESPONSE_BREACHED")
                && n.getUser().getId().equals(reporter.getId())).count());
    }
}
