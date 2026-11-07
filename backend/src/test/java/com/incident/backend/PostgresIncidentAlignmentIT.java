package com.incident.backend;

import com.incident.backend.dto.IncidentCreateRequest;
import com.incident.backend.dto.IncidentUpdateRequest;
import com.incident.backend.dto.StateChangeRequest;
import com.incident.backend.model.*;
import com.incident.backend.repository.*;
import com.incident.backend.model.AuditLog;
import com.incident.backend.service.CommentService;
import com.incident.backend.service.IncidentService;
import com.incident.backend.service.IncidentWorkflowService;
import com.incident.backend.service.UserService;
import com.incident.backend.service.AuditLogService;
import com.incident.backend.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("postgres-it")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestDatabaseGuard.class)
@AutoConfigureMockMvc
@Transactional
@EnabledIfSystemProperty(named = "postgres.integration", matches = "true")
class PostgresIncidentAlignmentIT {
    @Autowired IncidentService incidentService;
    @Autowired IncidentWorkflowService workflowService;
    @Autowired CommentService commentService;
    @Autowired UserRepository userRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired TechnicianRepository technicianRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired AffectedServiceRepository serviceRepository;
    @Autowired SlaPolicyRepository slaPolicyRepository;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtService jwtService;
    @Autowired UserService userService;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired IncidentRepository incidentRepository;
    @MockitoSpyBean AuditLogService auditLogService;

    private User user(Role role) {
        User user = new User();
        user.setName("Phase 2 Test");
        user.setEmail("phase2-" + UUID.randomUUID() + "@example.test");
        user.setPasswordHash("test-hash");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    private IncidentCreateRequest request() {
        Category category = categoryRepository.findAll(Sort.by("id")).get(0);
        AffectedService service = serviceRepository.findAll(Sort.by("id")).get(0);
        SlaPolicy policy = slaPolicyRepository.findAll(Sort.by("id")).get(0);
        return new IncidentCreateRequest("PostgreSQL rollback-safe phase 2 test",
                category.getId(), service.getId(), policy.getId(), Impact.HIGH, Urgency.HIGH,
                BusinessCriticality.HIGH, policy.getPriority());
    }

    @Test
    @Rollback
    void createsRetrievesAndUpdatesIncident() {
        User reporter = user(Role.ADMIN);
        var created = incidentService.create(request(), reporter);
        assertEquals(IncidentStatus.REPORTED, created.status());
        assertEquals(SlaStatus.NOT_STARTED, created.slaStatus());
        assertEquals(reporter.getId().longValue(), created.reporter().id());
        assertNotNull(created.createdAt());

        var retrieved = incidentService.get(created.id(), reporter);
        assertEquals(created.id(), retrieved.id());
        assertEquals(created.category().id(), retrieved.category().id());
        assertEquals(created.service().id(), retrieved.service().id());
        assertEquals(created.slaPolicy().id(), retrieved.slaPolicy().id());

        var updated = incidentService.update(created.id(), new IncidentUpdateRequest(
                "Updated description", null, null, null, null, null, null, null,
                "investigated", null, null), reporter);
        assertEquals("Updated description", updated.description());
        assertEquals("investigated", updated.investigationDetails());
    }

    @Test
    @Rollback
    void assignsIncidentToTechnicianEntity() {
        User reporter = user(Role.EMPLOYEE);
        User technicianUser = user(Role.EMPLOYEE);
        userService.updateRole(technicianUser.getId().longValue(), Role.TECHNICIAN, user(Role.ADMIN));
        Technician technician = technicianRepository.findByUserId(technicianUser.getId()).orElseThrow();
        var created = incidentService.create(request(), reporter);

        var assigned = workflowService.assign(created.id(), technician.getId().longValue());
        assertNotNull(assigned.assignedTechnician());
        assertEquals(technician.getId().longValue(), assigned.assignedTechnician().id());
        assertEquals(technicianUser.getId().longValue(), assigned.assignedTechnician().userId());
    }

    @Test
    @Rollback
    void createsAndRetrievesCommentsForIncident() {
        User reporter = user(Role.EMPLOYEE);
        var created = incidentService.create(request(), reporter);
        var comment = commentService.add(created.id(), "Rollback-safe comment", reporter);

        assertEquals(created.id(), comment.incidentId());
        assertEquals(reporter.getId().longValue(), comment.author().id());
        assertEquals("Rollback-safe comment", comment.text());
        assertEquals(1, commentService.list(created.id(), reporter).size());
        var history = auditLogRepository.findByIncidentIdOrderByTimestampAscIdAsc(Math.toIntExact(created.id()));
        assertTrue(history.stream().anyMatch(log -> log.getAction().equals("COMMENT_CREATED")
                && log.getActor().getId().equals(reporter.getId())));
    }

    @Test
    @Rollback
    void freeTextAuditSnapshotsRecordPresenceWithoutContent() throws Exception {
        User reporter = user(Role.ADMIN);
        var created = incidentService.create(request(), reporter);

        String descriptionBefore = "description-private-before";
        String descriptionAfter = "description-private-after";
        String investigationAfter = "investigation-private-after";
        String resolutionAfter = "resolution-private-after";
        String closureAfter = "closure-private-after";
        incidentService.update(created.id(), new IncidentUpdateRequest(descriptionBefore, null, null, null,
                null, null, null, null, investigationAfter, resolutionAfter, closureAfter), reporter);

        AuditLog firstUpdate = auditLogRepository
                .findByIncidentIdOrderByTimestampAscIdAsc(Math.toIntExact(created.id())).get(1);
        JsonNode firstOld = objectMapper.readTree(firstUpdate.getOldValue());
        JsonNode firstNew = objectMapper.readTree(firstUpdate.getNewValue());
        assertEquals("PRESENT", firstOld.get("description").asText());
        assertEquals("PRESENT", firstNew.get("description").asText());
        for (String field : List.of("investigationDetails", "resolutionDetails", "closureDetails")) {
            assertEquals("EMPTY", firstOld.get(field).asText(), field);
            assertEquals("PRESENT", firstNew.get(field).asText(), field);
        }

        long countAfterChange = auditLogRepository.countByIncidentId(Math.toIntExact(created.id()));
        incidentService.update(created.id(), new IncidentUpdateRequest(descriptionBefore, null, null, null,
                null, null, null, null, investigationAfter, resolutionAfter, closureAfter), reporter);
        assertEquals(countAfterChange, auditLogRepository.countByIncidentId(Math.toIntExact(created.id())),
                "unchanged free-text values must not create an audit event");

        incidentService.update(created.id(), new IncidentUpdateRequest(descriptionAfter, null, null, null,
                null, null, null, null, "", null, ""), reporter);
        List<AuditLog> history = auditLogRepository
                .findByIncidentIdOrderByTimestampAscIdAsc(Math.toIntExact(created.id()));
        AuditLog emptyingUpdate = history.get(2);
        JsonNode secondOld = objectMapper.readTree(emptyingUpdate.getOldValue());
        JsonNode secondNew = objectMapper.readTree(emptyingUpdate.getNewValue());
        assertEquals("PRESENT", secondOld.get("description").asText());
        assertEquals("PRESENT", secondNew.get("description").asText());
        for (String field : List.of("investigationDetails", "closureDetails")) {
            assertEquals("PRESENT", secondOld.get(field).asText(), field);
            assertEquals("EMPTY", secondNew.get(field).asText(), field);
        }
        assertFalse(secondOld.has("resolutionDetails"), "unchanged and omitted fields must not be included");
        assertFalse(secondNew.has("resolutionDetails"), "unchanged and omitted fields must not be included");

        String serializedSnapshots = history.stream()
                .map(log -> String.valueOf(log.getOldValue()) + String.valueOf(log.getNewValue()))
                .reduce("", String::concat);
        for (String privateText : List.of(descriptionBefore, descriptionAfter, investigationAfter,
                resolutionAfter, closureAfter)) {
            assertFalse(serializedSnapshots.contains(privateText), "audit snapshots must not contain free text");
        }
        assertTrue(serializedSnapshots.contains("PRESENT"));
        assertTrue(serializedSnapshots.contains("EMPTY"));
    }

    @Test
    @Rollback
    void auditsCreationChangedFieldsAssignmentStatusResolutionClosureAndChronologicalHistory() throws Exception {
        User reporter = user(Role.EMPLOYEE);
        User admin = user(Role.ADMIN);
        var created = incidentService.create(request(), reporter);
        List<AuditLog> afterCreate = auditLogRepository
                .findByIncidentIdOrderByTimestampAscIdAsc(Math.toIntExact(created.id()));
        assertEquals(1, afterCreate.size());
        assertEquals("INCIDENT_CREATED", afterCreate.get(0).getAction());
        assertEquals(reporter.getId(), afterCreate.get(0).getActor().getId());
        assertEquals(created.id().intValue(), afterCreate.get(0).getIncident().getId());

        incidentService.update(created.id(), new IncidentUpdateRequest("Changed description", null, null, null,
                null, null, null, null, null, null, null), reporter);
        List<AuditLog> afterUpdate = auditLogRepository
                .findByIncidentIdOrderByTimestampAscIdAsc(Math.toIntExact(created.id()));
        assertEquals(2, afterUpdate.size(), "same values must not create extra field audit rows");
        AuditLog update = afterUpdate.get(1);
        assertEquals("INCIDENT_UPDATED", update.getAction());
        assertTrue(update.getDescription().contains("description"));
        assertEquals("PRESENT", objectMapper.readTree(update.getOldValue()).path("description").asText());
        assertEquals("PRESENT", objectMapper.readTree(update.getNewValue()).path("description").asText());
        incidentService.update(created.id(), new IncidentUpdateRequest("Changed description", null, null, null,
                null, null, null, null, null, null, null), reporter);
        assertEquals(2, auditLogRepository.countByIncidentId(Math.toIntExact(created.id())),
                "unchanged values must not create another audit event");
        incidentService.update(created.id(), new IncidentUpdateRequest(null, null, null, null,
                Impact.MEDIUM, null, null, null, null, null, null), admin);
        assertEquals(3, auditLogRepository.countByIncidentId(Math.toIntExact(created.id())));
        AuditLog classificationUpdate = auditLogRepository
                .findByIncidentIdOrderByTimestampAscIdAsc(Math.toIntExact(created.id())).get(2);
        assertTrue(classificationUpdate.getOldValue().contains("HIGH"));
        assertTrue(classificationUpdate.getNewValue().contains("MEDIUM"));

        User techUser = user(Role.EMPLOYEE);
        userService.updateRole(techUser.getId().longValue(), Role.TECHNICIAN, admin);
        Technician technician = technicianRepository.findByUserId(techUser.getId()).orElseThrow();
        workflowService.assign(created.id(), technician.getId().longValue(), admin);
        User secondTechUser = user(Role.EMPLOYEE);
        userService.updateRole(secondTechUser.getId().longValue(), Role.TECHNICIAN, admin);
        Technician secondTechnician = technicianRepository.findByUserId(secondTechUser.getId()).orElseThrow();
        workflowService.assign(created.id(), secondTechnician.getId().longValue(), admin);
        workflowService.assign(created.id(), null, admin);
        workflowService.changeState(created.id(), new StateChangeRequest(IncidentStatus.INVESTIGATING), admin);
        workflowService.changeState(created.id(), new StateChangeRequest(IncidentStatus.RESOLVED), admin);
        workflowService.changeState(created.id(), new StateChangeRequest(IncidentStatus.CLOSED), admin);
        commentService.add(created.id(), "Audit comment body must not be copied", reporter);

        List<AuditLog> history = auditLogRepository
                .findByIncidentIdOrderByTimestampAscIdAsc(Math.toIntExact(created.id()));
        assertEquals(List.of("INCIDENT_CREATED", "INCIDENT_UPDATED", "INCIDENT_UPDATED", "TECHNICIAN_ASSIGNMENT_CHANGED",
                        "TECHNICIAN_ASSIGNMENT_CHANGED", "TECHNICIAN_ASSIGNMENT_CHANGED",
                        "INCIDENT_STATUS_CHANGED", "INCIDENT_RESOLVED", "INCIDENT_CLOSED", "COMMENT_CREATED"),
                history.stream().map(AuditLog::getAction).toList());
        assertTrue(history.get(3).getOldValue() == null);
        assertTrue(history.get(3).getNewValue().contains(technician.getId().toString()));
        assertTrue(history.get(4).getOldValue().contains(technician.getId().toString()));
        assertTrue(history.get(4).getNewValue().contains(secondTechnician.getId().toString()));
        assertTrue(history.get(5).getOldValue().contains(secondTechnician.getId().toString()));
        assertNull(history.get(5).getNewValue());
        assertTrue(history.get(6).getOldValue().contains("REPORTED"));
        assertTrue(history.get(6).getNewValue().contains("INVESTIGATING"));
        assertTrue(history.get(7).getNewValue().contains("RESOLVED"));
        assertTrue(history.get(8).getNewValue().contains("CLOSED"));
        assertFalse(history.get(9).getNewValue().contains("Audit comment body"));
        for (int i = 1; i < history.size(); i++) {
            assertFalse(history.get(i).getTimestamp().isBefore(history.get(i - 1).getTimestamp()));
        }

        String token = jwtService.generateToken(reporter);
        mvc.perform(get("/api/incidents/{id}/audit", created.id()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("INCIDENT_CREATED"))
                .andExpect(jsonPath("$[0].actorUserId").value(reporter.getId()));
        User other = user(Role.EMPLOYEE);
        mvc.perform(get("/api/incidents/{id}/audit", created.id())
                        .header("Authorization", "Bearer " + jwtService.generateToken(other)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/incidents/{id}", created.id())
                        .header("Authorization", "Bearer " + jwtService.generateToken(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Incident cannot be deleted because it has audit history"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void auditPersistenceFailureRollsBackIncidentCreation() {
        User reporter = transactionTemplate.execute(status -> user(Role.ADMIN));
        long incidentCountBefore = incidentRepository.count();
        try {
            doThrow(new IllegalStateException("forced audit persistence failure"))
                    .when(auditLogService).record(any());
            assertThrows(IllegalStateException.class, () -> transactionTemplate.execute(status -> {
                incidentService.create(request(), reporter);
                return null;
            }));
            assertEquals(incidentCountBefore, incidentRepository.count());
        } finally {
            transactionTemplate.executeWithoutResult(status -> userRepository.deleteById(reporter.getId()));
        }
    }

    @Test
    @Rollback
    void incidentRoutesUseThePostgresItSmPayloadAndTechnicianId() throws Exception {
        User admin = user(Role.ADMIN);
        String token = jwtService.generateToken(admin);
        String body = mvc.perform(post("/api/incidents")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REPORTED"))
                .andExpect(jsonPath("$.priority").isString())
                .andExpect(jsonPath("$.responseSlaStatus").value("NOT_STARTED"))
                .andExpect(jsonPath("$.resolutionSlaStatus").value("NOT_STARTED"))
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(body);
        long incidentId = created.path("id").asLong();

        mvc.perform(get("/api/incidents/{id}", incidentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reporter.email").value(admin.getEmail()))
                .andExpect(jsonPath("$.category.id").value(request().categoryId()));

        IncidentUpdateRequest update = new IncidentUpdateRequest("Updated through HTTP", null, null, null,
                null, null, null, null, "investigated", null, null);
        mvc.perform(put("/api/incidents/{id}", incidentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated through HTTP"));

        User technicianUser = user(Role.EMPLOYEE);
        userService.updateRole(technicianUser.getId().longValue(), Role.TECHNICIAN, admin);
        Technician technician = technicianRepository.findByUserId(technicianUser.getId()).orElseThrow();
        mvc.perform(get("/api/users/assignees").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(technician.getId()));
        mvc.perform(patch("/api/incidents/{id}/assign", incidentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.incident.backend.dto.AssignRequest(
                                technician.getId().longValue()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTechnician.id").value(technician.getId()));

        mvc.perform(get("/api/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").isString());
        mvc.perform(get("/api/services").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").isString());
        mvc.perform(get("/api/sla-policies").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].priority").isString());

        mvc.perform(post("/api/incidents/{id}/comments", incidentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"PostgreSQL comment route\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("PostgreSQL comment route"));
        mvc.perform(get("/api/incidents/{id}/comments", incidentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].text").value("PostgreSQL comment route"));
    }
}
