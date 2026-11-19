package com.incident.backend.service;

import com.incident.backend.model.AffectedService;
import com.incident.backend.model.Category;
import com.incident.backend.model.Incident;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DuplicateIncidentMatcherTest {
    private final DescriptionSimilarity similarity = new DescriptionSimilarity();
    private final DuplicateIncidentMatcher matcher = new DuplicateIncidentMatcher(similarity);

    @Test
    void ignoresCasePunctuationAndCommonFillerWords() {
        assertEquals(1.0, similarity.compare("Email SERVER unavailable!", "email server unavailable"));
    }

    @Test
    void sameCategoryAndServiceAreInsufficientWithoutDescriptionSimilarity() {
        Incident candidate = incident(1, 2, "Laptop keyboard is broken and needs replacement");

        assertEquals(0.0, matcher.score("Email server unavailable for all users", 1, 2, candidate));
    }

    @Test
    void similarDescriptionAndMatchingMetadataPassTheThreshold() {
        Incident candidate = incident(1, 2, "Email server unavailable for all users after restart");

        assertTrue(matcher.score("Email server unavailable for all users", 1, 2, candidate) >= 0.55);
    }

    private Incident incident(int categoryId, int serviceId, String description) {
        Category category = mock(Category.class);
        when(category.getId()).thenReturn(categoryId);
        AffectedService service = mock(AffectedService.class);
        when(service.getId()).thenReturn(serviceId);
        Incident incident = mock(Incident.class);
        when(incident.getCategory()).thenReturn(category);
        when(incident.getService()).thenReturn(service);
        when(incident.getDescription()).thenReturn(description);
        return incident;
    }
}
