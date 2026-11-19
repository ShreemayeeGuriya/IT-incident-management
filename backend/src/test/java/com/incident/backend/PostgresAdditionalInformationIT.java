package com.incident.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incident.backend.dto.IncidentCreateRequest;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
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
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired IncidentService incidentService;
    @Autowired IncidentWorkflowService workflowService;
    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired TechnicianRepository technicianRepository;
    @Autowired IncidentRepository incidentRepository;
    @Autowired IncidentInformationRequestRepository informationRequestRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired AffectedServiceRepository serviceRepository;
    @Autowired SlaPolicyRepository slaPolicyRepository;
    @Autowired JwtService jwtService;

    private User user(String label, Role role) {
        User user = new User();
        user.setName("Additional info " + label);
        user.setEmail("additional-info-" + label + "-" + UUID.randomUUID() + "@example.test");
        user.setPasswordHash("test-hash");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    private User technician(User admin) {
        User employee = user("technician", Role.EMPLOYEE);
        userService.updateRole(employee.getId().longValue(), Role.TECHNICIAN, admin);
        return userRepository.findById(employee.getId()).orElseThrow();
    }

    private long assignedIncident(User reporter, User admin, User techUser) {
        Category category = categoryRepository.findAll(Sort.by("id")).get(0);
        AffectedService affectedService = serviceRepository.findAll(Sort.by("id")).get(0);
        SlaPolicy policy = slaPolicyRepository.findByPriority(Priority.P3).orElseThrow();
        long incidentId = incidentService.create(new IncidentCreateRequest("Information request test incident",
                category.getId(), affectedService.getId(), policy.getId(), Impact.MEDIUM, Urgency.MEDIUM,
                BusinessCriticality.MEDIUM, policy.getPriority()), reporter).id();
        Technician technician = technicianRepository.findByUserId(techUser.getId()).orElseThrow();
        workflowService.assign(incidentId, technician.getId().longValue(), admin);
        return incidentId;
    }

    private String bearer(User user) { return "Bearer " + jwtService.generateToken(user); }

    private long createRequest(long incidentId, User tech) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("message", "Please provide the error timestamp"));
        String response = mvc.perform(post("/api/incidents/{id}/information-requests", incidentId)
                        .header("Authorization", bearer(tech)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    @Rollback
    void requestAndResponsePersistNotifyActorsAndAppearInIncidentAudit() throws Exception {
        User admin = user("admin", Role.ADMIN);
        User reporter = user("reporter", Role.EMPLOYEE);
        User tech = technician(admin);
        long incidentId = assignedIncident(reporter, admin, tech);
        long requestId = createRequest(incidentId, tech);

        var savedRequest = informationRequestRepository.findById(Math.toIntExact(requestId)).orElseThrow();
        assertEquals("Please provide the error timestamp", savedRequest.getRequestMessage());
        assertEquals(reporter.getId(), savedRequest.getReporter().getId());
        assertEquals(tech.getId(), savedRequest.getRequestingTechnician().getId());
        assertNotNull(savedRequest.getRequestedAt());

        String responseBody = objectMapper.writeValueAsString(java.util.Map.of("message", "The error began at 10:42 UTC"));
        mvc.perform(post("/api/incidents/{id}/information-requests/{requestId}/response", incidentId, requestId)
                        .header("Authorization", bearer(reporter)).contentType(MediaType.APPLICATION_JSON)
                        .content(responseBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESPONDED"))
                .andExpect(jsonPath("$.responseMessage").value("The error began at 10:42 UTC"));

        var persisted = informationRequestRepository.findById(Math.toIntExact(requestId)).orElseThrow();
        assertEquals(InformationRequestStatus.RESPONDED, persisted.getStatus());
        assertEquals("The error began at 10:42 UTC", persisted.getResponseMessage());
        assertNotNull(persisted.getRespondedAt());
        assertEquals(IncidentStatus.ASSIGNED,
                incidentRepository.findById(Math.toIntExact(incidentId)).orElseThrow().getStatus(),
                "additional-information messages must not alter the incident lifecycle");
        mvc.perform(get("/api/incidents/{id}/information-requests", incidentId)
                        .header("Authorization", bearer(reporter)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(requestId))
                .andExpect(jsonPath("$[0].status").value("RESPONDED"));

        List<AuditLog> infoAudits = auditLogRepository.findByIncidentIdOrderByTimestampAscIdAsc(
                        Math.toIntExact(incidentId)).stream()
                .filter(a -> a.getAction().startsWith("ADDITIONAL_INFORMATION_"))
                .toList();
        assertEquals(List.of("ADDITIONAL_INFORMATION_REQUESTED", "ADDITIONAL_INFORMATION_RESPONDED"),
                infoAudits.stream().map(AuditLog::getAction).toList());
        assertEquals(tech.getId(), infoAudits.get(0).getActor().getId());
        assertEquals(reporter.getId(), infoAudits.get(1).getActor().getId());
        assertTrue(infoAudits.stream().allMatch(a -> a.getIncident().getId().longValue() == incidentId));

        var reporterNotifications = notificationRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(
                reporter.getId(), PageRequest.of(0, 100)).getContent();
        var techNotifications = notificationRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(
                tech.getId(), PageRequest.of(0, 100)).getContent();
        assertTrue(reporterNotifications.stream().anyMatch(n -> n.getNotificationType()
                .equals("ADDITIONAL_INFORMATION_REQUESTED") && n.getIncident().getId().longValue() == incidentId));
        assertTrue(techNotifications.stream().anyMatch(n -> n.getNotificationType()
                .equals("ADDITIONAL_INFORMATION_RESPONDED") && n.getIncident().getId().longValue() == incidentId));
    }

    @Test
    @Rollback
    void enforcesAssignedTechnicianAndReporterOwnership() throws Exception {
        User admin = user("admin", Role.ADMIN);
        User reporter = user("reporter", Role.EMPLOYEE);
        User otherReporter = user("other-reporter", Role.EMPLOYEE);
        User tech = technician(admin);
        User otherTech = technician(admin);
        long incidentId = assignedIncident(reporter, admin, tech);

        mvc.perform(post("/api/incidents/{id}/information-requests", incidentId)
                        .header("Authorization", bearer(otherTech)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Please send details\"}"))
                .andExpect(status().isForbidden());

        long requestId = createRequest(incidentId, tech);
        mvc.perform(post("/api/incidents/{id}/information-requests/{requestId}/response", incidentId, requestId)
                        .header("Authorization", bearer(otherReporter)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Here are the details\"}"))
                .andExpect(status().isForbidden());
        assertEquals(InformationRequestStatus.OPEN,
                informationRequestRepository.findById(Math.toIntExact(requestId)).orElseThrow().getStatus());
    }

    @Test
    @Rollback
    void rejectsBlankMessagesAndDuplicateResponse() throws Exception {
        User admin = user("admin", Role.ADMIN);
        User reporter = user("reporter", Role.EMPLOYEE);
        User tech = technician(admin);
        long incidentId = assignedIncident(reporter, admin, tech);

        mvc.perform(post("/api/incidents/{id}/information-requests", incidentId)
                        .header("Authorization", bearer(tech)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest());
        long requestId = createRequest(incidentId, tech);
        String response = "{\"message\":\"The requested detail is attached\"}";
        mvc.perform(post("/api/incidents/{id}/information-requests/{requestId}/response", incidentId, requestId)
                        .header("Authorization", bearer(reporter)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/incidents/{id}/information-requests/{requestId}/response", incidentId, requestId)
                        .header("Authorization", bearer(reporter)).contentType(MediaType.APPLICATION_JSON)
                        .content(response))
                .andExpect(status().isOk());
        mvc.perform(post("/api/incidents/{id}/information-requests/{requestId}/response", incidentId, requestId)
                        .header("Authorization", bearer(reporter)).contentType(MediaType.APPLICATION_JSON)
                        .content(response))
                .andExpect(status().isConflict());
        assertEquals(1, auditLogRepository.findByIncidentIdOrderByTimestampAscIdAsc(Math.toIntExact(incidentId))
                .stream().filter(a -> a.getAction().equals("ADDITIONAL_INFORMATION_RESPONDED")).count());
    }
}
