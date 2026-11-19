package com.incident.backend;

import com.incident.backend.dto.DuplicateIncidentCheckRequest;
import com.incident.backend.dto.IncidentCreateRequest;
import com.incident.backend.model.*;
import com.incident.backend.repository.AffectedServiceRepository;
import com.incident.backend.repository.CategoryRepository;
import com.incident.backend.repository.IncidentRepository;
import com.incident.backend.repository.UserRepository;
import com.incident.backend.security.JwtService;
import com.incident.backend.service.DuplicateIncidentService;
import com.incident.backend.service.IncidentService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("postgres-it")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestDatabaseGuard.class)
@AutoConfigureMockMvc
@Transactional
@EnabledIfSystemProperty(named = "postgres.integration", matches = "true")
class PostgresDuplicateIncidentDetectionIT {
    @Autowired DuplicateIncidentService duplicateIncidentService;
    @Autowired IncidentService incidentService;
    @Autowired IncidentRepository incidentRepository;
    @Autowired UserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired AffectedServiceRepository serviceRepository;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @Autowired ObjectMapper objectMapper;

    @Test
    @Rollback
    void postgresCandidateQueryReturnsRankedRelevantMatchesAndNoUnrelatedCategoryOnlyMatches() {
        User reporter = user(Role.EMPLOYEE);
        Category category = categoryRepository.findAll(Sort.by("id")).get(0);
        AffectedService service = serviceRepository.findAll(Sort.by("id")).get(0);
        Incident matching = create(reporter, category, service,
                "Email server unavailable for all users after restart");
        create(reporter, category, service, "The office chair wheel is broken");

        var suggestions = duplicateIncidentService.findSuggestions(
                new DuplicateIncidentCheckRequest("Email server unavailable for all users", category.getId(), service.getId()),
                reporter);

        assertEquals(1, suggestions.size());
        assertEquals(matching.getId().longValue(), suggestions.get(0).incidentId());
        assertEquals(matching.getDescription(), suggestions.get(0).description());
        assertEquals(category.getName(), suggestions.get(0).category());
        assertEquals(service.getName(), suggestions.get(0).service());
        assertEquals(matching.getStatus(), suggestions.get(0).status());
        assertTrue(suggestions.get(0).similarityScore() >= 0.55);
    }

    @Test
    @Rollback
    void employeesOnlyReceiveSuggestionsForIncidentsTheyCanView() {
        User reporter = user(Role.EMPLOYEE);
        User otherReporter = user(Role.EMPLOYEE);
        Category category = categoryRepository.findAll(Sort.by("id")).get(0);
        AffectedService service = serviceRepository.findAll(Sort.by("id")).get(0);
        create(otherReporter, category, service, "Email server unavailable for all users");

        var suggestions = duplicateIncidentService.findSuggestions(
                new DuplicateIncidentCheckRequest("Email server unavailable for all users", category.getId(), service.getId()),
                reporter);

        assertTrue(suggestions.isEmpty());
    }

    @Test
    @Rollback
    void authenticatedApiReturnsSuggestionSummariesAndEmptyListWhenNoMatchesExist() throws Exception {
        User reporter = user(Role.EMPLOYEE);
        Category category = categoryRepository.findAll(Sort.by("id")).get(0);
        AffectedService service = serviceRepository.findAll(Sort.by("id")).get(0);
        create(reporter, category, service, "Email server unavailable for all users");
        String token = "Bearer " + jwtService.generateToken(reporter);
        var request = new DuplicateIncidentCheckRequest("Email server unavailable for all users",
                category.getId(), service.getId());

        mvc.perform(post("/api/incidents/duplicate-suggestions").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].incidentId").isNumber())
                .andExpect(jsonPath("$[0].description").value("Email server unavailable for all users"))
                .andExpect(jsonPath("$[0].category").value(category.getName()))
                .andExpect(jsonPath("$[0].service").value(service.getName()))
                .andExpect(jsonPath("$[0].status").value("REPORTED"))
                .andExpect(jsonPath("$[0].similarityScore").isNumber());

        var noMatchRequest = new DuplicateIncidentCheckRequest("Printer toner needs replacement",
                category.getId(), service.getId());
        mvc.perform(post("/api/incidents/duplicate-suggestions").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(noMatchRequest)))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    private Incident create(User reporter, Category category, AffectedService service, String description) {
        return incidentRepository.findById(Math.toIntExact(incidentService.create(
                new IncidentCreateRequest(description, category.getId(), service.getId(), null,
                        Impact.MEDIUM, Urgency.MEDIUM, BusinessCriticality.MEDIUM, null), reporter).id())).orElseThrow();
    }

    private User user(Role role) {
        User user = new User();
        user.setName("Duplicate test");
        user.setEmail("duplicate-it-" + UUID.randomUUID() + "@example.test");
        user.setPasswordHash("test-hash");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }
}
