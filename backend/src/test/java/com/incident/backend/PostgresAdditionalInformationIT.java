package com.incident.backend;

import com.incident.backend.dto.IncidentCreateRequest;
import com.incident.backend.model.*;
import com.incident.backend.repository.*;
import com.incident.backend.security.JwtService;
import com.incident.backend.service.IncidentService;
import com.incident.backend.service.IncidentWorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static com.incident.backend.model.IncidentStatus.ASSIGNED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("postgres-it")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestDatabaseGuard.class)
@AutoConfigureMockMvc
@Transactional
@EnabledIfSystemProperty(named = "postgres.integration", matches = "true")
class PostgresAdditionalInformationIT {
    @Autowired UserRepository userRepository;
    @Autowired TechnicianRepository technicianRepository;
    @Autowired IncidentRepository incidentRepository;
    @Autowired IncidentInformationRequestRepository requestRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired AffectedServiceRepository serviceRepository;
    @Autowired SlaPolicyRepository slaPolicyRepository;
    @Autowired IncidentService incidentService;
    @Autowired IncidentWorkflowService workflowService;
    @Autowired JwtService jwtService;
    @Autowired MockMvc mvc;

    private User user(Role role) {
        User user = new User();
        user.setName("Information Flow Test");
        user.setEmail("information-" + UUID.randomUUID() + "@example.test");
        user.setPasswordHash("test-hash");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    private long incident(User reporter, Technician technician) {
        Category category = categoryRepository.findAll(Sort.by("id")).get(0);
        AffectedService service = serviceRepository.findAll(Sort.by("id")).get(0);
        SlaPolicy policy = slaPolicyRepository.findByPriority(Priority.P3).orElseThrow();
        long id = incidentService.create(new IncidentCreateRequest("VPN access request needs investigation",
                category.getId(), service.getId(), policy.getId(), Impact.MEDIUM, Urgency.MEDIUM,
                BusinessCriticality.MEDIUM, policy.getPriority()), reporter).id();
        workflowService.assign(id, technician.getId().longValue(), technician.getUser());
        return id;
    }

    @Test
    @Rollback
    void assignedTechnicianRequestsAndReporterRespondsWithPersistedOrderedAudit() throws Exception {
        User reporter = user(Role.EMPLOYEE);
        User technicianUser = user(Role.TECHNICIAN);
        Technician technician = technicianRepository.saveAndFlush(new Technician(technicianUser));
        long incidentId = incident(reporter, technician);
        String technicianToken = jwtService.generateToken(technicianUser);
        String reporterToken = jwtService.generateToken(reporter);

        String created = mvc.perform(post("/api/incidents/{id}/information-requests", incidentId)
                        .header("Authorization", "Bearer " + technicianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Please confirm whether this affects your whole team.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        int requestId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created).get("id").asInt();

        mvc.perform(post("/api/incidents/{id}/information-requests/{requestId}/response", incidentId, requestId)
                        .header("Authorization", "Bearer " + reporterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"It affects my team only.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESPONDED"))
                .andExpect(jsonPath("$.responseMessage").value("It affects my team only."));

        IncidentInformationRequest stored = requestRepository.findById(requestId).orElseThrow();
        assertEquals(InformationRequestStatus.RESPONDED, stored.getStatus());
        assertEquals("It affects my team only.", stored.getResponseMessage());
        assertEquals(ASSIGNED, incidentRepository.findById(Math.toIntExact(incidentId)).orElseThrow().getStatus());
        var informationAudits = auditLogRepository.findByIncidentIdOrderByTimestampAscIdAsc(Math.toIntExact(incidentId)).stream()
                        .map(AuditLog::getAction).filter(action -> action.startsWith("ADDITIONAL_INFORMATION_"))
                        .toList();
        assertEquals(java.util.List.of("ADDITIONAL_INFORMATION_REQUESTED", "ADDITIONAL_INFORMATION_RESPONDED"),
                informationAudits);
        var auditEntries = auditLogRepository.findByIncidentIdOrderByTimestampAscIdAsc(Math.toIntExact(incidentId)).stream()
                .filter(entry -> entry.getAction().startsWith("ADDITIONAL_INFORMATION_"))
                .toList();
        assertEquals(technicianUser.getId(), auditEntries.get(0).getActor().getId());
        assertEquals(reporter.getId(), auditEntries.get(1).getActor().getId());

        mvc.perform(get("/api/incidents/{id}/information-requests", incidentId)
                        .header("Authorization", "Bearer " + reporterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(requestId))
                .andExpect(jsonPath("$[0].status").value("RESPONDED"));
    }

    @Test
    @Rollback
    void employeeCannotOpenRequestAndUnassignedTechnicianCannotRequest() throws Exception {
        User reporter = user(Role.EMPLOYEE);
        User technicianUser = user(Role.TECHNICIAN);
        Category category = categoryRepository.findAll(Sort.by("id")).get(0);
        AffectedService service = serviceRepository.findAll(Sort.by("id")).get(0);
        SlaPolicy policy = slaPolicyRepository.findByPriority(Priority.P3).orElseThrow();
        long incidentId = incidentService.create(new IncidentCreateRequest("VPN access request needs investigation",
                category.getId(), service.getId(), policy.getId(), Impact.MEDIUM, Urgency.MEDIUM,
                BusinessCriticality.MEDIUM, policy.getPriority()), reporter).id();

        mvc.perform(post("/api/incidents/{id}/information-requests", incidentId)
                        .header("Authorization", "Bearer " + jwtService.generateToken(reporter))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"More details\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/incidents/{id}/information-requests", incidentId)
                        .header("Authorization", "Bearer " + jwtService.generateToken(technicianUser))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"More details\"}"))
                .andExpect(status().isForbidden());
    }
}
