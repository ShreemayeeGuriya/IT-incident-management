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

    @Test
    void nearlyIdenticalIncidentDescriptionsMatch() {
        Incident candidate = incident(1, 2,
                "The office Wi-Fi is disconnecting repeatedly. Employees are unable to access internal applications or the internet reliably. The connection drops every few minutes.");

        double score = matcher.score(
                "The office Wi-Fi keeps disconnecting repeatedly. Employees cannot reliably access internal applications or the internet.",
                1, 2, candidate);

        assertTrue(score >= 0.55);
    }

    @Test
    void differentWordingForSameWifiProblemMatches() {
        Incident candidate = incident(1, 2,
                "The office Wi-Fi is disconnecting repeatedly. Employees are unable to access internal applications or the internet reliably. The connection drops every few minutes.");

        double score = matcher.score(
                "Users across the office are experiencing unstable wireless connectivity. Wi-Fi keeps dropping and staff cannot reliably connect to company applications.",
                1, 2, candidate);

        assertTrue(score >= 0.48, "Expected semantically equivalent wireless connectivity reports to match; got " + score);
    }

    @Test
    void reportedOfficeWirelessPairMatchesWithItsExactWording() {
        Incident candidate = incident(1, 2,
                " The office Wi-Fi is disconnecting repeatedly. Employees are unable to access internal applications or the internet reliably. The connection drops every few minutes.");

        double score = matcher.score(
                "Several employees are facing frequent interruptions while using the office wireless network. Their devices repeatedly lose connectivity, making it difficult to access company resources and complete their work.",
                1, 2, candidate);

        assertTrue(score >= 0.48, "Expected exact regression pair to clear the match threshold; got " + score);
    }

    @Test
    void databasePerformanceAndQueryTimeoutDescriptionsMatchAcrossDifferentWording() {
        assertSimilar(
                "The company database is responding extremely slowly. Employees are experiencing long delays when loading records, and some database queries are timing out.",
                "Users are unable to retrieve information from the application in a reasonable amount of time. Database operations are taking too long, and requests frequently fail due to timeout errors.");
    }

    @Test
    void networkWifiConnectivityPairMatchesAcrossSynonyms() {
        assertSimilar(
                "The office Wi-Fi is disconnecting repeatedly. Employees are unable to access internal applications or the internet reliably. The connection drops every few minutes.",
                "Several employees are facing frequent interruptions while using the office wireless network. Their devices repeatedly lose connectivity, making it difficult to access company resources and complete their work.");
    }

    @Test
    void paymentFailureDescriptionsMatchAcrossSynonyms() {
        assertSimilar(
                "Online payment transactions are failing at checkout. Customers see a transaction declined error and cannot complete purchases.",
                "Checkout charges are not going through. Users receive failed payment messages and purchases remain incomplete.");
    }

    @Test
    void authenticationFailureDescriptionsMatchAcrossSynonyms() {
        assertSimilar(
                "Employees cannot log into corporate accounts. Authentication rejects valid passwords with unauthorized errors.",
                "Staff sign-in is failing for valid credentials; identity verification denies access to the company portal.");
    }

    @Test
    void unrelatedIncidentsWithinTheSameCategoriesDoNotMatch() {
        assertUnrelated("The company database is responding extremely slowly and queries time out.",
                "A database user needs read permission to a new finance table for an audit.");
        assertUnrelated("Office Wi-Fi keeps disconnecting and users lose connectivity.",
                "The network printer needs its driver reinstalled after a paper tray replacement.");
        assertUnrelated("Online payment transactions fail at checkout with declined errors.",
                "The monthly payment reconciliation report needs its column widths corrected.");
        assertUnrelated("Employees cannot log into their accounts because authentication rejects valid passwords.",
                "A new employee needs a building security badge activated for office entry.");
    }

    @Test
    void unrelatedProblemsDoNotPassTheThreshold() {
        Incident candidate = incident(1, 2, "The office Wi-Fi is disconnecting repeatedly and users cannot connect.");

        assertEquals(0.0, matcher.score("The database backup failed during overnight maintenance", 1, 2, candidate));
        assertEquals(0.0, matcher.score("The office printer needs a new toner cartridge", 1, 2, candidate));
    }

    @Test
    void emptyAndVeryShortDescriptionsAreNotSimilar() {
        assertEquals(0.0, similarity.compare("", "Wi-Fi keeps disconnecting"));
        assertEquals(0.0, similarity.compare("wifi", "wifi"));
        assertEquals(0.0, similarity.compare("Wi-Fi", "network"));
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

    private void assertSimilar(String first, String second) {
        double score = matcher.score(first, 1, 2, incident(1, 2, second));
        assertTrue(score >= 0.48, "Expected related descriptions to match, score was " + score);
    }

    private void assertUnrelated(String first, String second) {
        assertEquals(0.0, matcher.score(first, 1, 2, incident(1, 2, second)),
                "Same-category metadata must not make unrelated descriptions match");
    }
}
