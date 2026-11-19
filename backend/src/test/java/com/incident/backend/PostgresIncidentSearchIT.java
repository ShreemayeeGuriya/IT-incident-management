package com.incident.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.incident.backend.dto.IncidentCreateRequest;
import com.incident.backend.model.*;
import com.incident.backend.repository.*;
import com.incident.backend.security.JwtService;
import com.incident.backend.service.IncidentService;
import com.incident.backend.service.IncidentWorkflowService;
import com.incident.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
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
class PostgresIncidentSearchIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired IncidentService incidentService;
    @Autowired IncidentWorkflowService workflowService;
    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired TechnicianRepository technicianRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired AffectedServiceRepository serviceRepository;
    @Autowired SlaPolicyRepository slaPolicyRepository;
    @Autowired JwtService jwtService;

    private User admin;
    private Category selectedCategory;
    private Category otherCategory;
    private Technician technicianA;
    private Technician technicianB;
    private long selectedP1Incident;
    private long selectedP3Incident;
    private long otherCategoryP1Incident;
    private String searchMarker;

    @BeforeEach
    void setUpFixtures() {
        searchMarker = "filtertest" + UUID.randomUUID().toString().replace("-", "");
        admin = user(Role.ADMIN);
        User techUserA = technicianUser(admin);
        User techUserB = technicianUser(admin);
        technicianA = technicianRepository.findByUserId(techUserA.getId()).orElseThrow();
        technicianB = technicianRepository.findByUserId(techUserB.getId()).orElseThrow();

        List<Category> categories = categoryRepository.findAll(Sort.by("id"));
        assertTrue(categories.size() >= 2, "integration database must contain at least two categories");
        selectedCategory = categories.get(0);
        otherCategory = categories.get(1);

        selectedP1Incident = createAssigned(searchMarker + "-alpha", selectedCategory, true, technicianA);
        selectedP3Incident = createAssigned(searchMarker + "-beta", selectedCategory, false, technicianB);
        otherCategoryP1Incident = createAssigned(searchMarker + "-other-alpha", otherCategory, true, technicianA);
    }

    @Test
    @Rollback
    void filtersByCategoryAndKeepsPaginationAndResponseShape() throws Exception {
        JsonNode response = incidents("categoryId", selectedCategory.getId().toString(), "size", "1");
        assertTrue(response.get("totalElements").asInt() >= 2);
        assertEquals(1, response.get("size").asInt());
        assertEquals(1, response.get("content").size());
        assertEquals(selectedCategory.getId(), response.at("/content/0/category/id").asInt());
        assertTrue(ids(incidents("categoryId", selectedCategory.getId().toString(), "search", searchMarker))
                .containsAll(List.of(selectedP1Incident, selectedP3Incident)));
    }

    @Test
    @Rollback
    void combinesCategoryWithPriorityAndStatusTechnicianFilters() throws Exception {
        JsonNode byCategoryAndPriority = incidents("categoryId", selectedCategory.getId().toString(),
                "priority", "P1", "search", searchMarker);
        assertEquals(List.of(selectedP1Incident), ids(byCategoryAndPriority));

        JsonNode byCategoryStatusAndTechnician = incidents("categoryId", selectedCategory.getId().toString(),
                "status", "ASSIGNED", "technicianId", technicianA.getId().toString(), "search", searchMarker);
        assertEquals(List.of(selectedP1Incident), ids(byCategoryStatusAndTechnician));
    }

    @Test
    @Rollback
    void combinesCategoryWithTextSearchAndLeavesExistingTextSearchUnaffected() throws Exception {
        JsonNode categoryAndSearch = incidents("categoryId", selectedCategory.getId().toString(),
                "search", searchMarker);
        assertEquals(2, ids(categoryAndSearch).size());
        assertTrue(ids(categoryAndSearch).containsAll(List.of(selectedP1Incident, selectedP3Incident)));

        JsonNode searchOnly = incidents("search", searchMarker);
        assertEquals(3, searchOnly.get("totalElements").asInt());
        assertEquals(3, ids(searchOnly).size());
        assertTrue(ids(searchOnly).containsAll(List.of(selectedP1Incident, selectedP3Incident,
                otherCategoryP1Incident)));
    }

    @Test
    @Rollback
    void returnsAnEmptyPageWhenValidFiltersHaveNoMatches() throws Exception {
        JsonNode response = incidents("categoryId", otherCategory.getId().toString(), "priority", "P3",
                "search", searchMarker);
        assertEquals(0, response.get("totalElements").asInt());
        assertEquals(0, response.get("content").size());
    }

    @Test
    @Rollback
    void rejectsInvalidAndUnknownCategoryIdsClearly() throws Exception {
        mvc.perform(get("/api/incidents").param("categoryId", "-1")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/incidents").param("categoryId", "2147483647")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/incidents").param("categoryId", "not-a-number")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    private User user(Role role) {
        User user = new User();
        user.setName("Incident search test");
        user.setEmail("incident-search-" + UUID.randomUUID() + "@example.test");
        user.setPasswordHash("test-hash");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    private User technicianUser(User actorAdmin) {
        User employee = user(Role.EMPLOYEE);
        userService.updateRole(employee.getId().longValue(), Role.TECHNICIAN, actorAdmin);
        return userRepository.findById(employee.getId()).orElseThrow();
    }

    private long createAssigned(String description, Category category, boolean highPriority,
                                Technician technician) {
        AffectedService service = serviceRepository.findAll(Sort.by("id")).get(0);
        Priority priority = highPriority ? Priority.P1 : Priority.P3;
        SlaPolicy policy = slaPolicyRepository.findByPriority(priority).orElseThrow();
        Impact impact = highPriority ? Impact.HIGH : Impact.MEDIUM;
        Urgency urgency = highPriority ? Urgency.HIGH : Urgency.MEDIUM;
        BusinessCriticality criticality = highPriority ? BusinessCriticality.HIGH : BusinessCriticality.MEDIUM;
        long id = incidentService.create(new IncidentCreateRequest(description, category.getId(), service.getId(),
                policy.getId(), impact, urgency, criticality, priority), admin).id();
        workflowService.assign(id, technician.getId().longValue(), admin);
        return id;
    }

    private JsonNode incidents(String... parameters) throws Exception {
        var request = get("/api/incidents").header("Authorization", bearer(admin));
        for (int i = 0; i < parameters.length; i += 2) request = request.param(parameters[i], parameters[i + 1]);
        MvcResult result = mvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private List<Long> ids(JsonNode page) {
        List<Long> values = new ArrayList<>();
        page.get("content").forEach(item -> values.add(item.get("id").asLong()));
        return values;
    }

    private String bearer(User user) { return "Bearer " + jwtService.generateToken(user); }
}
