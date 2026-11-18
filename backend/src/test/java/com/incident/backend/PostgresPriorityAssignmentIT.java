package com.incident.backend;

import com.incident.backend.assignment.AssignmentService;
import com.incident.backend.assignment.LoadBasedAssignmentStrategy;
import com.incident.backend.assignment.RoundRobinAssignmentStrategy;
import com.incident.backend.assignment.SkillBasedAssignmentStrategy;
import com.incident.backend.dto.IncidentCreateRequest;
import com.incident.backend.dto.IncidentUpdateRequest;
import com.incident.backend.dto.StateChangeRequest;
import com.incident.backend.exception.BadRequestException;
import com.incident.backend.exception.IncidentNotEligibleException;
import com.incident.backend.exception.NoTechnicianAvailableException;
import com.incident.backend.exception.TechnicianUnavailableException;
import com.incident.backend.exception.TechnicianWorkloadExceededException;
import com.incident.backend.model.*;
import com.incident.backend.priority.ImpactUrgencyMatrixPriorityStrategy;
import com.incident.backend.priority.PriorityService;
import com.incident.backend.priority.WeightedScorePriorityStrategy;
import com.incident.backend.repository.*;
import com.incident.backend.security.JwtService;
import com.incident.backend.service.IncidentService;
import com.incident.backend.service.IncidentWorkflowService;
import com.incident.backend.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.incident.backend.model.IncidentStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Stages 2-4: priority calculation, assignment strategies and assignment rules against PostgreSQL. */
@SpringBootTest
@ActiveProfiles("postgres-it")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestDatabaseGuard.class)
@AutoConfigureMockMvc
@Transactional
@EnabledIfSystemProperty(named = "postgres.integration", matches = "true")
class PostgresPriorityAssignmentIT {
    @Autowired PriorityService priorityService;
    @Autowired AssignmentService assignmentService;
    @Autowired RoundRobinAssignmentStrategy roundRobin;
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
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwtService;
    @Autowired MockMvc mvc;
    @Autowired jakarta.persistence.EntityManager entityManager;

    private User admin;

    @BeforeEach
    void isolateFromOtherTechnicians() {
        // Technicians left by other test classes must not be picked. Rolled back after each test.
        jdbc.update("UPDATE technicians SET availability_status = 'UNAVAILABLE'");
        jdbc.update("INSERT INTO skills (skill_name) SELECT category_name FROM categories "
                + "ON CONFLICT (skill_name) DO NOTHING");
        admin = user(Role.ADMIN);
    }

    @AfterEach
    void restoreDefaults() {
        priorityService.strategies().select(WeightedScorePriorityStrategy.NAME);
        assignmentService.strategies().select(SkillBasedAssignmentStrategy.NAME);
        ReflectionTestUtils.setField(roundRobin, "lastChosenId", null);
    }

    // ---------------------------------------------------------------- helpers

    private User user(Role role) {
        User user = new User();
        user.setName("Stage Test " + role);
        user.setEmail("stage-" + UUID.randomUUID() + "@example.test");
        user.setPasswordHash("test-hash");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    private Technician technician(String... skills) {
        User techUser = user(Role.EMPLOYEE);
        userService.updateRole(techUser.getId().longValue(), Role.TECHNICIAN, admin);
        Technician technician = technicianRepository.findByUserId(techUser.getId()).orElseThrow();
        assignmentService.replaceSkills(technician.getId(), List.of(skills));
        return technician;
    }

    private Category category(String name) {
        return categoryRepository.findAll(Sort.by("id")).stream()
                .filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
    }

    private long incident(String categoryName, Impact impact, Urgency urgency, BusinessCriticality criticality,
                          Priority priority) {
        AffectedService service = serviceRepository.findAll(Sort.by("id")).get(0);
        return incidentService.create(new IncidentCreateRequest("Stage test incident",
                category(categoryName).getId(), service.getId(), null, impact, urgency, criticality, priority),
                admin).id();
    }

    /** A PRIORITIZED incident with no technician, ready for auto-assignment. */
    private long prioritized(String categoryName) {
        long id = incident(categoryName, Impact.MEDIUM, Urgency.MEDIUM, BusinessCriticality.MEDIUM, Priority.P3);
        workflowService.changeState(id, new StateChangeRequest(CLASSIFIED), admin);
        workflowService.changeState(id, new StateChangeRequest(PRIORITIZED), admin);
        return id;
    }

    /** Changes a technician row directly, then drops cached entities so the change is seen (as in a new request). */
    private void updateTechnician(String setClause, Integer technicianId) {
        entityManager.flush();
        jdbc.update("UPDATE technicians SET " + setClause + " WHERE technician_id = ?", technicianId);
        entityManager.clear();
    }

    private Incident reload(long id) {
        return incidentRepository.findById(Math.toIntExact(id)).orElseThrow();
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    // ---------------------------------------------------------------- Stage 2: priority

    @Test
    @Rollback
    void recalculationRaisesPriorityAndMovesTheSlaDeadline() {
        // The same factors produce P4 under weighted score and P3 under the matrix strategy.
        long id = incident("Network", Impact.LOW, Urgency.LOW, BusinessCriticality.HIGH, Priority.P4);
        priorityService.strategies().select(ImpactUrgencyMatrixPriorityStrategy.NAME);
        Instant oldDeadline = reload(id).getResolutionDeadline();

        PriorityService.Recalculation r = priorityService.recalculate(id, admin);

        assertTrue(r.changed());
        assertEquals(Priority.P3, r.assessment().priority());
        assertEquals(3, r.assessment().score());
        Incident incident = reload(id);
        assertEquals(Priority.P3, incident.getPriority());
        assertEquals(Priority.P3, incident.getSlaPolicy().getPriority());
        assertTrue(incident.getResolutionDeadline().isBefore(oldDeadline), "P3 deadline is sooner than P4");
        assertTrue(auditLogRepository.findByIncidentIdOrderByTimestampAscIdAsc(Math.toIntExact(id)).stream()
                .anyMatch(a -> "INCIDENT_UPDATED".equals(a.getAction()) && a.getNewValue().contains("P3")));
    }

    @Test
    @Rollback
    void creationCalculatesPriorityAndRejectsAnInconsistentSuppliedPriority() {
        AffectedService service = serviceRepository.findAll(Sort.by("id")).get(0);
        IncidentCreateRequest automatic = new IncidentCreateRequest("Automatic priority test",
                category("Network").getId(), service.getId(), null, Impact.HIGH, Urgency.HIGH,
                BusinessCriticality.HIGH, null);

        long id = incidentService.create(automatic, admin).id();

        Incident saved = reload(id);
        assertEquals(Priority.P1, saved.getPriority());
        assertEquals(Priority.P1, saved.getSlaPolicy().getPriority());
        assertEquals(saved.getCreatedAt().plusSeconds(saved.getSlaPolicy().getResponseTimeMinutes() * 60L),
                saved.getResponseDeadline());
        assertEquals(saved.getCreatedAt().plusSeconds(saved.getSlaPolicy().getResolutionTimeMinutes() * 60L),
                saved.getResolutionDeadline());

        IncidentCreateRequest inconsistent = new IncidentCreateRequest("Inconsistent priority test",
                category("Network").getId(), service.getId(), null, Impact.HIGH, Urgency.HIGH,
                BusinessCriticality.HIGH, Priority.P4);
        assertThrows(BadRequestException.class, () -> incidentService.create(inconsistent, admin));
    }

    @Test
    @Rollback
    void eachChangedClassificationFactorRecalculatesPriorityInPostgres() {
        long impactId = incident("Network", Impact.MEDIUM, Urgency.MEDIUM,
                BusinessCriticality.MEDIUM, Priority.P3);
        incidentService.update(impactId, updateRequest(Impact.HIGH, null, null, null), admin);
        assertEquals(Priority.P2, reload(impactId).getPriority());

        long urgencyId = incident("Network", Impact.MEDIUM, Urgency.MEDIUM,
                BusinessCriticality.MEDIUM, Priority.P3);
        incidentService.update(urgencyId, updateRequest(null, Urgency.HIGH, null, null), admin);
        assertEquals(Priority.P2, reload(urgencyId).getPriority());

        long criticalityId = incident("Network", Impact.MEDIUM, Urgency.MEDIUM,
                BusinessCriticality.MEDIUM, Priority.P3);
        incidentService.update(criticalityId, updateRequest(null, null, BusinessCriticality.HIGH, null), admin);
        assertEquals(Priority.P2, reload(criticalityId).getPriority());

        long multipleId = incident("Network", Impact.MEDIUM, Urgency.MEDIUM,
                BusinessCriticality.MEDIUM, Priority.P3);
        incidentService.update(multipleId, updateRequest(Impact.HIGH, Urgency.HIGH,
                BusinessCriticality.HIGH, null), admin);
        assertEquals(Priority.P1, reload(multipleId).getPriority());
    }

    @Test
    @Rollback
    void unchangedCalculatedPriorityDoesNotAddPriorityToAuditSnapshot() {
        long id = incident("Network", Impact.MEDIUM, Urgency.MEDIUM,
                BusinessCriticality.MEDIUM, Priority.P3);

        incidentService.update(id, updateRequest(Impact.LOW, null, null, null), admin);

        assertEquals(Priority.P3, reload(id).getPriority());
        AuditLog update = auditLogRepository.findByIncidentIdOrderByTimestampAscIdAsc(Math.toIntExact(id)).stream()
                .filter(a -> "INCIDENT_UPDATED".equals(a.getAction())).findFirst().orElseThrow();
        assertTrue(update.getNewValue().contains("impact"));
        assertFalse(update.getOldValue() != null && update.getOldValue().contains("priority"));
        assertFalse(update.getNewValue() != null && update.getNewValue().contains("priority"));
    }

    @Test
    @Rollback
    void priorityChangingUpdatePersistsAuditContextPolicyAndDeadlines() {
        long id = incident("Network", Impact.MEDIUM, Urgency.MEDIUM,
                BusinessCriticality.MEDIUM, Priority.P3);
        Incident before = reload(id);
        Instant oldResponseDeadline = before.getResponseDeadline();
        Instant oldResolutionDeadline = before.getResolutionDeadline();
        Instant beforeUpdate = Instant.now();

        incidentService.update(id, updateRequest(Impact.HIGH, Urgency.HIGH,
                BusinessCriticality.HIGH, null), admin);

        Incident after = reload(id);
        assertEquals(Priority.P1, after.getPriority());
        assertEquals(Priority.P1, after.getSlaPolicy().getPriority());
        assertEquals(after.getCreatedAt().plusSeconds(after.getSlaPolicy().getResponseTimeMinutes() * 60L),
                after.getResponseDeadline());
        assertEquals(after.getCreatedAt().plusSeconds(after.getSlaPolicy().getResolutionTimeMinutes() * 60L),
                after.getResolutionDeadline());
        assertTrue(after.getResponseDeadline().isBefore(oldResponseDeadline));
        assertTrue(after.getResolutionDeadline().isBefore(oldResolutionDeadline));

        AuditLog update = auditLogRepository.findByIncidentIdOrderByTimestampAscIdAsc(Math.toIntExact(id)).stream()
                .filter(a -> "INCIDENT_UPDATED".equals(a.getAction()))
                .filter(a -> a.getNewValue() != null && a.getNewValue().contains("P1"))
                .findFirst().orElseThrow();
        assertEquals("P3", extractAuditValue(update.getOldValue(), "priority"));
        assertEquals("P1", extractAuditValue(update.getNewValue(), "priority"));
        assertEquals(admin.getId(), update.getActor().getId());
        assertEquals(Math.toIntExact(id), update.getIncident().getId());
        assertNotNull(update.getTimestamp());
        assertFalse(update.getTimestamp().isBefore(beforeUpdate));
    }

    @ParameterizedTest
    @EnumSource(value = IncidentStatus.class, names = {"RESOLVED", "VERIFIED", "CLOSED"})
    @Rollback
    void factorChangeAndExplicitRecalculationAreRejectedInTerminalStates(IncidentStatus status) {
        long id = incidentAtStatus(status);
        Incident before = reload(id);

        assertThrows(IncidentNotEligibleException.class,
                () -> incidentService.update(id, updateRequest(Impact.HIGH, null, null, null), admin));
        assertThrows(IncidentNotEligibleException.class, () -> priorityService.recalculate(id, admin));
        assertEquals(before.getImpact(), reload(id).getImpact());
        incidentService.update(id, new IncidentUpdateRequest("Terminal description update", null, null, null,
                null, null, null, null, null, null, null), admin);
        assertEquals("Terminal description update", reload(id).getDescription());
    }

    private IncidentUpdateRequest updateRequest(Impact impact, Urgency urgency,
                                               BusinessCriticality criticality, Priority priority) {
        return new IncidentUpdateRequest(null, null, null, null, impact, urgency, criticality,
                priority, null, null, null);
    }

    private long incidentAtStatus(IncidentStatus target) {
        Technician tech = technician("Network");
        long id = prioritized("Network");
        workflowService.assign(id, tech.getId().longValue(), admin);
        workflowService.changeState(id, new StateChangeRequest(INVESTIGATING), admin);
        incidentService.update(id, new IncidentUpdateRequest(null, null, null, null, null, null, null,
                null, null, "Completed investigation", null), admin);
        workflowService.changeState(id, new StateChangeRequest(RESOLVED), admin);
        if (target == VERIFIED || target == CLOSED) {
            workflowService.changeState(id, new StateChangeRequest(VERIFIED), admin);
        }
        if (target == CLOSED) workflowService.changeState(id, new StateChangeRequest(CLOSED), admin);
        return id;
    }

    private String extractAuditValue(String json, String field) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).path(field).asText();
        } catch (Exception exception) {
            throw new AssertionError("Could not read audit snapshot", exception);
        }
    }

    @Test
    @Rollback
    void unchangedPriorityIsNotSavedAgain() {
        long id = incident("Network", Impact.MEDIUM, Urgency.MEDIUM, BusinessCriticality.MEDIUM, Priority.P3);
        long audits = auditLogRepository.countByIncidentId(Math.toIntExact(id));
        assertFalse(priorityService.recalculate(id, admin).changed());
        assertEquals(audits, auditLogRepository.countByIncidentId(Math.toIntExact(id)));
    }

    @Test
    @Rollback
    void switchingStrategyChangesTheResult() {
        long id = incident("Network", Impact.LOW, Urgency.LOW, BusinessCriticality.HIGH, Priority.P4);
        assertEquals(Priority.P4, priorityService.assess(id, admin).priority(), "weighted: 1*1*3 = 3");
        priorityService.strategies().select(ImpactUrgencyMatrixPriorityStrategy.NAME);
        assertEquals(Priority.P3, priorityService.assess(id, admin).priority(), "matrix: P4 raised by HIGH");
        assertEquals(Priority.P3, priorityService.recalculate(id, admin).incident().priority());
    }

    @Test
    @Rollback
    void priorityIsLockedOnceResolved() {
        Technician tech = technician("Network");
        long id = prioritized("Network");
        workflowService.assign(id, tech.getId().longValue(), admin);
        workflowService.changeState(id, new StateChangeRequest(INVESTIGATING), admin);
        incidentService.update(id, new IncidentUpdateRequest(null, null, null, null, null, null, null, null, null,
                "Replaced the faulty switch", null), admin);
        workflowService.changeState(id, new StateChangeRequest(RESOLVED), admin);

        assertThrows(IncidentNotEligibleException.class, () -> priorityService.recalculate(id, admin));
    }

    @Test
    @Rollback
    void previewApiReturnsTheAssessment() throws Exception {
        mvc.perform(post("/api/priority/preview").header("Authorization", bearer(user(Role.EMPLOYEE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"impact\":\"HIGH\",\"urgency\":\"HIGH\",\"businessCriticality\":\"MEDIUM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("P1"))
                .andExpect(jsonPath("$.score").value(18))
                .andExpect(jsonPath("$.strategy").value("WEIGHTED_SCORE"));
    }

    @Test
    @Rollback
    void onlyStaffCanRecalculateAndOnlyAdminsCanSwitchStrategy() throws Exception {
        User employee = user(Role.EMPLOYEE);
        long id = incident("Network", Impact.HIGH, Urgency.HIGH, BusinessCriticality.HIGH, Priority.P1);
        mvc.perform(post("/api/incidents/{id}/priority/recalculate", id).header("Authorization", bearer(employee)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/priority/strategy").header("Authorization", bearer(employee))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"strategy\":\"IMPACT_URGENCY_MATRIX\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/priority/strategy").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"strategy\":\"IMPACT_URGENCY_MATRIX\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value("IMPACT_URGENCY_MATRIX"));
        mvc.perform(put("/api/priority/strategy").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"strategy\":\"NOPE\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- Stage 3: assignment strategies

    @Test
    @Rollback
    void skillBasedAssignsTheMatchingTechnicianAndMovesToAssigned() {
        technician("Hardware");
        Technician network = technician("Network");
        long id = prioritized("Network");

        AssignmentService.Result result = assignmentService.autoAssign(id, admin);

        assertEquals("SKILL_BASED", result.strategy());
        assertEquals(network.getId(), result.technician().technicianId());
        Incident incident = reload(id);
        assertEquals(ASSIGNED, incident.getStatus());
        assertEquals(network.getId(), incident.getAssignedTechnician().getId());
        List<String> actions = auditLogRepository.findByIncidentIdOrderByTimestampAscIdAsc(Math.toIntExact(id))
                .stream().map(AuditLog::getAction).toList();
        assertTrue(actions.contains("TECHNICIAN_ASSIGNMENT_CHANGED"), actions.toString());
    }

    @Test
    @Rollback
    void skillBasedPrefersTheLessLoadedSkilledTechnician() {
        Technician busy = technician("Network");
        Technician free = technician("Network");
        workflowService.assign(prioritized("Network"), busy.getId().longValue(), admin);

        long id = prioritized("Network");
        assertEquals(free.getId(), assignmentService.autoAssign(id, admin).technician().technicianId());
    }

    @Test
    @Rollback
    void skillBasedFailsClearlyWhenNobodyHasTheSkill() {
        technician("Hardware");
        long id = prioritized("Network");
        NoTechnicianAvailableException ex = assertThrows(NoTechnicianAvailableException.class,
                () -> assignmentService.autoAssign(id, admin));
        assertTrue(ex.getMessage().contains("'Network'"));
        assertEquals(PRIORITIZED, reload(id).getStatus());
    }

    @Test
    @Rollback
    void loadBasedIgnoresSkills() {
        assignmentService.strategies().select(LoadBasedAssignmentStrategy.NAME);
        Technician first = technician("Hardware");
        Technician second = technician("Hardware");
        workflowService.assign(prioritized("Network"), first.getId().longValue(), admin);
        assertEquals(second.getId(), assignmentService.autoAssign(prioritized("Network"), admin)
                .technician().technicianId());
    }

    @Test
    @Rollback
    void roundRobinTakesTurns() {
        assignmentService.strategies().select(RoundRobinAssignmentStrategy.NAME);
        Technician a = technician();
        Technician b = technician();
        assertEquals(a.getId(), assignmentService.autoAssign(prioritized("Network"), admin).technician().technicianId());
        assertEquals(b.getId(), assignmentService.autoAssign(prioritized("Network"), admin).technician().technicianId());
        assertEquals(a.getId(), assignmentService.autoAssign(prioritized("Network"), admin).technician().technicianId());
    }

    @Test
    @Rollback
    void recommendationDoesNotAssign() {
        Technician network = technician("Network");
        long id = prioritized("Network");
        assertEquals(network.getId(), assignmentService.recommend(id).technician().technicianId());
        assertNull(reload(id).getAssignedTechnician());
        assertEquals(PRIORITIZED, reload(id).getStatus());
    }

    // ---------------------------------------------------------------- Stage 4: business rules

    @Test
    @Rollback
    void technicianAtMaximumWorkloadIsSkippedAndReportedAsFull() {
        Technician only = technician("Network");
        updateTechnician("max_workload = 1", only.getId());
        workflowService.assign(prioritized("Network"), only.getId().longValue(), admin);

        long id = prioritized("Network");
        NoTechnicianAvailableException ex = assertThrows(NoTechnicianAvailableException.class,
                () -> assignmentService.autoAssign(id, admin));
        assertTrue(ex.getMessage().contains("maximum workload"));
        assertThrows(TechnicianWorkloadExceededException.class, () -> assignmentService.requireCanTake(only.getId()));
    }

    @Test
    @Rollback
    void unavailableTechnicianIsNeverPicked() {
        Technician away = technician("Network");
        updateTechnician("availability_status = 'BUSY'", away.getId());
        assertThrows(NoTechnicianAvailableException.class, () -> assignmentService.autoAssign(prioritized("Network"), admin));
        assertThrows(TechnicianUnavailableException.class, () -> assignmentService.requireCanTake(away.getId()));
    }

    @Test
    @Rollback
    void manualAssignmentRejectsUnavailableTechnician() {
        Technician away = technician("Network");
        long incidentId = prioritized("Network");
        updateTechnician("availability_status = 'BUSY'", away.getId());

        assertThrows(TechnicianUnavailableException.class,
                () -> workflowService.assign(incidentId, away.getId().longValue(), admin));
        assertNull(reload(incidentId).getAssignedTechnician());
        assertEquals(PRIORITIZED, reload(incidentId).getStatus());
    }

    @Test
    @Rollback
    void manualAssignmentRejectsTechnicianAtMaximumWorkload() {
        Technician only = technician("Network");
        updateTechnician("max_workload = 1", only.getId());
        workflowService.assign(prioritized("Network"), only.getId().longValue(), admin);
        long secondIncident = prioritized("Network");

        assertThrows(TechnicianWorkloadExceededException.class,
                () -> workflowService.assign(secondIncident, only.getId().longValue(), admin));
        assertNull(reload(secondIncident).getAssignedTechnician());
        assertEquals(PRIORITIZED, reload(secondIncident).getStatus());
    }

    @Test
    @Rollback
    void resolvedIncidentsNoLongerCountTowardsWorkload() {
        Technician tech = technician("Network");
        updateTechnician("max_workload = 1", tech.getId());
        long first = prioritized("Network");
        workflowService.assign(first, tech.getId().longValue(), admin);
        workflowService.changeState(first, new StateChangeRequest(INVESTIGATING), admin);
        incidentService.update(first, new IncidentUpdateRequest(null, null, null, null, null, null, null, null, null,
                "Fixed", null), admin);
        workflowService.changeState(first, new StateChangeRequest(RESOLVED), admin);

        assertEquals(tech.getId(), assignmentService.autoAssign(prioritized("Network"), admin).technician().technicianId());
    }

    @Test
    @Rollback
    void onlyPrioritizedUnassignedIncidentsCanBeAutoAssigned() {
        technician("Network");
        long reported = incident("Network", Impact.LOW, Urgency.LOW, BusinessCriticality.LOW, Priority.P4);
        IncidentNotEligibleException ex = assertThrows(IncidentNotEligibleException.class,
                () -> assignmentService.autoAssign(reported, admin));
        assertTrue(ex.getMessage().contains("classify and prioritize"));

        long assigned = prioritized("Network");
        assignmentService.autoAssign(assigned, admin);
        assertThrows(IncidentNotEligibleException.class, () -> assignmentService.autoAssign(assigned, admin));
    }

    @Test
    @Rollback
    void autoAssignApiReturns409WhenNobodyIsAvailable() throws Exception {
        long id = prioritized("Network");
        mvc.perform(post("/api/incidents/{id}/auto-assign", id).header("Authorization", bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("No technician available")));
    }

    @Test
    @Rollback
    void autoAssignApiAssigns() throws Exception {
        Technician network = technician("Network");
        long id = prioritized("Network");
        mvc.perform(post("/api/incidents/{id}/auto-assign", id).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("SKILL_BASED"))
                .andExpect(jsonPath("$.technician.technicianId").value(network.getId()))
                .andExpect(jsonPath("$.incident.status").value("ASSIGNED"));
    }

    @Test
    @Rollback
    void skillsCanBeManagedAndUnknownSkillsAreRejected() throws Exception {
        Technician tech = technician();
        mvc.perform(put("/api/technicians/{id}/skills", tech.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"skills\":[\"network\",\"Hardware\"]}"))
                .andExpect(status().isOk());
        assertEquals(Set.of("Hardware", "Network"), assignmentService.skillsOf(tech.getId()));
        mvc.perform(put("/api/technicians/{id}/skills", tech.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"skills\":[\"Juggling\"]}"))
                .andExpect(status().isBadRequest());
        assertEquals(Set.of("Hardware", "Network"), assignmentService.skillsOf(tech.getId()),
                "a rejected update leaves the skills unchanged");
    }

    @Test
    @Rollback
    void workloadReportCountsOpenIncidents() {
        Technician tech = technician("Network");
        workflowService.assign(prioritized("Network"), tech.getId().longValue(), admin);
        workflowService.assign(prioritized("Network"), tech.getId().longValue(), admin);
        AssignmentService.TechnicianSummary row = assignmentService.workload().stream()
                .filter(s -> s.technicianId().equals(tech.getId())).findFirst().orElseThrow();
        assertEquals(2, row.activeIncidents());
        assertEquals(Set.of("Network"), row.skills());
    }
}
