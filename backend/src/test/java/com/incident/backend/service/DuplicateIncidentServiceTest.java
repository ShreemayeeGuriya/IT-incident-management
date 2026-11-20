package com.incident.backend.service;

import com.incident.backend.dto.DuplicateIncidentCheckRequest;
import com.incident.backend.model.AffectedService;
import com.incident.backend.model.Category;
import com.incident.backend.model.Incident;
import com.incident.backend.model.Role;
import com.incident.backend.model.User;
import com.incident.backend.repository.AffectedServiceRepository;
import com.incident.backend.repository.CategoryRepository;
import com.incident.backend.repository.IncidentRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DuplicateIncidentServiceTest {
    @Test
    void exactOfficeWirelessRegressionProducesOnlyTheRelevantServiceSuggestion() {
        IncidentRepository incidents = mock(IncidentRepository.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        AffectedServiceRepository services = mock(AffectedServiceRepository.class);
        when(categories.existsById(1)).thenReturn(true);
        when(services.existsById(2)).thenReturn(true);

        Incident matching = incident(10, 1, 2,
                " The office Wi-Fi is disconnecting repeatedly. Employees are unable to access internal applications or the internet reliably. The connection drops every few minutes.");
        Incident unrelated = incident(11, 1, 2, "The office printer needs a new toner cartridge");
        when(incidents.findDuplicateCandidates(null, null)).thenReturn(List.of(matching, unrelated));

        User admin = mock(User.class);
        when(admin.getRole()).thenReturn(Role.ADMIN);
        DuplicateIncidentService service = new DuplicateIncidentService(incidents, categories, services,
                new DuplicateIncidentMatcher(new DescriptionSimilarity()));
        var results = service.findSuggestions(new DuplicateIncidentCheckRequest(
                "Several employees are facing frequent interruptions while using the office wireless network. Their devices repeatedly lose connectivity, making it difficult to access company resources and complete their work.",
                1, 2), admin);

        assertEquals(1, results.size());
        assertEquals(10L, results.get(0).incidentId());
        assertTrue(results.get(0).similarityScore() >= 0.48);
    }

    @Test
    void databaseTimeoutRegressionFlowsThroughCandidateRankingAndExcludesSameCategoryNoise() {
        IncidentRepository incidents = mock(IncidentRepository.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        AffectedServiceRepository services = mock(AffectedServiceRepository.class);
        when(categories.existsById(1)).thenReturn(true);
        when(services.existsById(2)).thenReturn(true);
        Incident matching = incident(20, 1, 2,
                "The company database is responding extremely slowly. Employees are experiencing long delays when loading records, and some database queries are timing out.");
        Incident unrelated = incident(21, 1, 2,
                "A database user needs read permission to a new finance table for an audit.");
        when(incidents.findDuplicateCandidates(null, null)).thenReturn(List.of(unrelated, matching));

        User admin = mock(User.class);
        when(admin.getRole()).thenReturn(Role.ADMIN);
        DuplicateIncidentService service = new DuplicateIncidentService(incidents, categories, services,
                new DuplicateIncidentMatcher(new DescriptionSimilarity()));
        var results = service.findSuggestions(new DuplicateIncidentCheckRequest(
                "Users are unable to retrieve information from the application in a reasonable amount of time. Database operations are taking too long, and requests frequently fail due to timeout errors.",
                1, 2), admin);

        assertEquals(1, results.size());
        assertEquals(20L, results.get(0).incidentId());
        assertTrue(results.get(0).similarityScore() >= 0.48);
    }

    @Test
    void returnsPotentialMatchesInDescendingSimilarityOrder() {
        IncidentRepository incidents = mock(IncidentRepository.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        AffectedServiceRepository services = mock(AffectedServiceRepository.class);
        when(categories.existsById(1)).thenReturn(true);
        when(services.existsById(2)).thenReturn(true);

        Incident weaker = incident(10, 1, 2, "Email service unavailable for some users after maintenance");
        Incident stronger = incident(11, 1, 2, "Email server unavailable for all users after restart");
        when(incidents.findDuplicateCandidates(null, null)).thenReturn(List.of(weaker, stronger));

        User admin = mock(User.class);
        when(admin.getRole()).thenReturn(Role.ADMIN);
        DuplicateIncidentService service = new DuplicateIncidentService(incidents, categories, services,
                new DuplicateIncidentMatcher(new DescriptionSimilarity()));
        var results = service.findSuggestions(
                new DuplicateIncidentCheckRequest("Email server unavailable for all users", 1, 2), admin);

        assertEquals(2, results.size());
        assertEquals(11L, results.get(0).incidentId());
        assertEquals(10L, results.get(1).incidentId());
        assertTrue(results.get(0).similarityScore() > results.get(1).similarityScore());
    }

    private Incident incident(int id, int categoryId, int serviceId, String description) {
        Category category = mock(Category.class);
        when(category.getId()).thenReturn(categoryId);
        AffectedService affectedService = mock(AffectedService.class);
        when(affectedService.getId()).thenReturn(serviceId);
        Incident incident = mock(Incident.class);
        when(incident.getId()).thenReturn(id);
        when(incident.getCategory()).thenReturn(category);
        when(incident.getService()).thenReturn(affectedService);
        when(incident.getDescription()).thenReturn(description);
        when(incident.getCreatedAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        return incident;
    }
}
