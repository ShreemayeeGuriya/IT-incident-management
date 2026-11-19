package com.incident.backend;

import com.incident.backend.dto.IncidentCreateRequest;
import com.incident.backend.dto.IncidentResponse;
import com.incident.backend.dto.IncidentUpdateRequest;
import com.incident.backend.dto.StateChangeRequest;
import com.incident.backend.exception.InvalidStateTransitionException;
import com.incident.backend.model.*;
import com.incident.backend.repository.*;
import com.incident.backend.security.JwtService;
import com.incident.backend.service.IncidentService;
import com.incident.backend.service.IncidentWorkflowService;
import com.incident.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.incident.backend.model.IncidentStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Stage 1: the incident lifecycle is enforced end to end (service layer, persistence, audit and REST API). */
@SpringBootTest
@ActiveProfiles("postgres-it")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestDatabaseGuard.class)
@AutoConfigureMockMvc
@Transactional
@EnabledIfSystemProperty(named = "postgres.integration", matches = "true")
class PostgresIncidentLifecycleIT {
    @Autowired IncidentService incidentService;
    @Autowired IncidentWorkflowService workflowService;
    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired TechnicianRepository technicianRepository;
    @Autowired IncidentRepository incidentRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired AffectedServiceRepository serviceRepository;
    @Autowired SlaPolicyRepository slaPolicyRepository;
    @Autowired JwtService jwtService;
    @Autowired MockMvc mvc;

    private User user(Role role) {
        User user = new User();
        user.setName("Lifecycle Test");
        user.setEmail("lifecycle-" + UUID.randomUUID() + "@example.test");
        user.setPasswordHash("test-hash");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    private Technician technician(User admin) {
        User techUser = user(Role.EMPLOYEE);
        userService.updateRole(techUser.getId().longValue(), Role.TECHNICIAN, admin);
        return technicianRepository.findByUserId(techUser.getId()).orElseThrow();
    }

    private long newIncident(User reporter) {
        Category category = categoryRepository.findAll(Sort.by("id")).get(0);
        AffectedService service = serviceRepository.findAll(Sort.by("id")).get(0);
        SlaPolicy policy = slaPolicyRepository.findAll(Sort.by("id")).get(0);
        return incidentService.create(new IncidentCreateRequest("Payment application unavailable",
                category.getId(), service.getId(), policy.getId(), Impact.HIGH, Urgency.HIGH,
                BusinessCriticality.HIGH, policy.getPriority()), reporter).id();
    }

    private IncidentResponse move(long id, IncidentStatus status, User actor) {
        return workflowService.changeState(id, new StateChangeRequest(status), actor);
    }

    private void addResolution(long id, User actor) {
        incidentService.update(id, new IncidentUpdateRequest(null, null, null, null, null, null, null,
                null, null, "Restarted the payment gateway", null), actor);
    }

    /** Creation advances through CLASSIFIED and PRIORITIZED, then assignment moves to ASSIGNED. */
    private long assignedIncident(User admin, Technician technician) {
        long id = newIncident(admin);
        workflowService.assign(id, technician.getId().longValue(), admin);
        return id;
    }

    private Incident reload(long id) {
        return incidentRepository.findById(Math.toIntExact(id)).orElseThrow();
    }

    @Test
    @Rollback
    void fullLifecycleSetsTimestampsAndAuditsEveryStep() {
        User admin = user(Role.ADMIN);
        Technician technician = technician(admin);
        long id = assignedIncident(admin, technician);
        assertEquals(ASSIGNED, reload(id).getStatus());

        move(id, INVESTIGATING, admin);
        assertNotNull(reload(id).getFirstResponseAt());
        addResolution(id, admin);
        move(id, RESOLVED, admin);
        assertNotNull(reload(id).getResolvedAt());
        move(id, VERIFIED, admin);
        IncidentResponse closed = move(id, CLOSED, admin);
        assertEquals(CLOSED, closed.status());
        assertNotNull(reload(id).getClosedAt());

        List<String> statusAudits = auditLogRepository.findByIncidentIdOrderByTimestampAscIdAsc(Math.toIntExact(id))
                .stream().map(AuditLog::getAction)
                .filter(a -> a.startsWith("INCIDENT_STATUS") || a.equals("INCIDENT_RESOLVED")
                        || a.equals("INCIDENT_CLOSED"))
                .toList();
        assertEquals(7, statusAudits.size(), "one audit entry per status change: " + statusAudits);
    }

    @Test
    @Rollback
    void skippingStepsIsRejectedAndNothingIsSaved() {
        User admin = user(Role.ADMIN);
        long id = newIncident(admin);
        long auditsBefore = auditLogRepository.countByIncidentId(Math.toIntExact(id));

        InvalidStateTransitionException ex = assertThrows(InvalidStateTransitionException.class,
                () -> move(id, CLOSED, admin));
        assertEquals(PRIORITIZED, ex.getFrom());
        assertEquals(PRIORITIZED, reload(id).getStatus());
        assertEquals(auditsBefore, auditLogRepository.countByIncidentId(Math.toIntExact(id)));
    }

    @Test
    @Rollback
    void requestingTheCurrentStatusIsANoOp() {
        User admin = user(Role.ADMIN);
        long id = newIncident(admin);
        long auditsBefore = auditLogRepository.countByIncidentId(Math.toIntExact(id));
        assertEquals(PRIORITIZED, move(id, PRIORITIZED, admin).status());
        assertEquals(auditsBefore, auditLogRepository.countByIncidentId(Math.toIntExact(id)));
    }

    @Test
    @Rollback
    void assignedRequiresATechnicianWhenSetThroughTheStatusEndpoint() {
        User admin = user(Role.ADMIN);
        long id = newIncident(admin);
        assertThrows(InvalidStateTransitionException.class, () -> move(id, ASSIGNED, admin));
        assertEquals(PRIORITIZED, reload(id).getStatus());
    }

    @Test
    @Rollback
    void resolvingRequiresResolutionDetails() {
        User admin = user(Role.ADMIN);
        long id = assignedIncident(admin, technician(admin));
        move(id, INVESTIGATING, admin);
        InvalidStateTransitionException ex = assertThrows(InvalidStateTransitionException.class,
                () -> move(id, RESOLVED, admin));
        assertTrue(ex.getMessage().contains("resolution details"));
        assertEquals(INVESTIGATING, reload(id).getStatus());
    }

    @Test
    @Rollback
    void assigningPrioritizedIncidentMovesItToAssigned() {
        User admin = user(Role.ADMIN);
        long id = newIncident(admin);
        workflowService.assign(id, technician(admin).getId().longValue(), admin);
        assertEquals(ASSIGNED, reload(id).getStatus());
        assertNotNull(reload(id).getAssignedTechnician());
    }

    @Test
    @Rollback
    void unassigningAnAssignedIncidentReturnsItToPrioritized() {
        User admin = user(Role.ADMIN);
        long id = assignedIncident(admin, technician(admin));
        workflowService.assign(id, null, admin);
        Incident incident = reload(id);
        assertEquals(PRIORITIZED, incident.getStatus());
        assertNull(incident.getAssignedTechnician());
    }

    @Test
    @Rollback
    void movingBackToPrioritizedReleasesTheTechnician() {
        User admin = user(Role.ADMIN);
        long id = assignedIncident(admin, technician(admin));
        move(id, PRIORITIZED, admin);
        assertNull(reload(id).getAssignedTechnician());
    }

    @Test
    @Rollback
    void inProgressIncidentCanBeReassignedButNotLeftUnassigned() {
        User admin = user(Role.ADMIN);
        long id = assignedIncident(admin, technician(admin));
        move(id, INVESTIGATING, admin);

        assertThrows(InvalidStateTransitionException.class, () -> workflowService.assign(id, null, admin));
        Technician replacement = technician(admin);
        workflowService.assign(id, replacement.getId().longValue(), admin);
        Incident incident = reload(id);
        assertEquals(INVESTIGATING, incident.getStatus());
        assertEquals(replacement.getId(), incident.getAssignedTechnician().getId());
    }

    @Test
    @Rollback
    void closedIncidentCannotBeAssigned() {
        User admin = user(Role.ADMIN);
        Technician technician = technician(admin);
        long id = assignedIncident(admin, technician);
        move(id, INVESTIGATING, admin);
        addResolution(id, admin);
        move(id, RESOLVED, admin);
        move(id, VERIFIED, admin);
        move(id, CLOSED, admin);
        assertThrows(InvalidStateTransitionException.class,
                () -> workflowService.assign(id, technician(admin).getId().longValue(), admin));
    }

    @Test
    @Rollback
    void reopeningClearsTheResolvedTimestamp() {
        User admin = user(Role.ADMIN);
        long id = assignedIncident(admin, technician(admin));
        move(id, INVESTIGATING, admin);
        addResolution(id, admin);
        move(id, RESOLVED, admin);
        move(id, INVESTIGATING, admin);
        assertNull(reload(id).getResolvedAt());
        move(id, RESOLVED, admin);
        assertNotNull(reload(id).getResolvedAt());
    }

    @Test
    @Rollback
    void apiReturns409WithAllowedNextStatuses() throws Exception {
        User admin = user(Role.ADMIN);
        long id = newIncident(admin);
        mvc.perform(patch("/api/incidents/{id}/status", id)
                        .header("Authorization", "Bearer " + jwtService.generateToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.fieldErrors.from").value("PRIORITIZED"))
                .andExpect(jsonPath("$.fieldErrors.to").value("CLOSED"))
                .andExpect(jsonPath("$.fieldErrors.allowedNext").value("[ASSIGNED]"));
    }

    @Test
    @Rollback
    void apiAcceptsAValidTransition() throws Exception {
        User admin = user(Role.ADMIN);
        long id = newIncident(admin);
        Incident incident = reload(id);
        incident.setAssignedTechnician(technician(admin));
        incidentRepository.saveAndFlush(incident);

        mvc.perform(patch("/api/incidents/{id}/status", id)
                        .header("Authorization", "Bearer " + jwtService.generateToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ASSIGNED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
        assertEquals(ASSIGNED, reload(id).getStatus());
    }
}
