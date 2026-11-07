package com.incident.backend;

import com.incident.backend.dto.IncidentCreateRequest;
import com.incident.backend.model.*;
import com.incident.backend.repository.*;
import com.incident.backend.service.IncidentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("postgres-it")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestDatabaseGuard.class)
@Transactional
@EnabledIfSystemProperty(named = "postgres.integration", matches = "true")
class PostgresNotificationPersistenceIT {
    @Autowired NotificationRepository notificationRepository;
    @Autowired UserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired AffectedServiceRepository serviceRepository;
    @Autowired SlaPolicyRepository slaPolicyRepository;
    @Autowired IncidentRepository incidentRepository;
    @Autowired IncidentService incidentService;

    private User createUser() {
        User user = new User();
        user.setName("Notification persistence test");
        user.setEmail("notification-persistence-" + UUID.randomUUID() + "@example.test");
        user.setPasswordHash("test-hash");
        user.setRole(Role.EMPLOYEE);
        return userRepository.saveAndFlush(user);
    }

    private IncidentCreateRequest incidentRequest() {
        Category category = categoryRepository.findAll(Sort.by("id")).get(0);
        AffectedService service = serviceRepository.findAll(Sort.by("id")).get(0);
        SlaPolicy policy = slaPolicyRepository.findAll(Sort.by("id")).get(0);
        return new IncidentCreateRequest("Notification persistence fixture", category.getId(), service.getId(),
                policy.getId(), Impact.MEDIUM, Urgency.MEDIUM, BusinessCriticality.MEDIUM, policy.getPriority());
    }

    private Notification notification(User recipient, Incident incident, String eventKey) {
        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setIncident(incident);
        notification.setMessage("Incident requires attention");
        notification.setNotificationType("INCIDENT_CREATED");
        notification.setEventKey(eventKey);
        return notification;
    }

    @Test
    @Rollback
    void storesInAppNotificationAndAllowsOneEventForMultipleRecipients() {
        User firstRecipient = createUser();
        User secondRecipient = createUser();
        long incidentId = incidentService.create(incidentRequest(), firstRecipient).id();
        Incident incident = incidentRepository.findById(Math.toIntExact(incidentId)).orElseThrow();
        String eventId = UUID.randomUUID().toString();

        Notification first = notification(firstRecipient, incident,
                eventId + ":" + firstRecipient.getId());
        Notification second = notification(secondRecipient, incident,
                eventId + ":" + secondRecipient.getId());
        notificationRepository.saveAllAndFlush(java.util.List.of(first, second));

        var firstRecipientPage = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(
                firstRecipient.getId(), PageRequest.of(0, 10));
        assertEquals(1, firstRecipientPage.getTotalElements());
        assertEquals(eventId + ":" + firstRecipient.getId(), firstRecipientPage.getContent().get(0).getEventKey());
        assertEquals(NotificationDeliveryStatus.PENDING, firstRecipientPage.getContent().get(0).getDeliveryStatus());
        assertEquals(0, firstRecipientPage.getContent().get(0).getDeliveryAttempts());
        assertEquals(1, notificationRepository.countByUserIdAndIsReadFalse(firstRecipient.getId()));
        assertEquals(1, notificationRepository.findAllByUserIdOrderByCreatedAtDesc(
                secondRecipient.getId(), PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    @Rollback
    void rejectsDuplicateNonNullEventKey() {
        User recipient = createUser();
        long incidentId = incidentService.create(incidentRequest(), recipient).id();
        Incident incident = incidentRepository.findById(Math.toIntExact(incidentId)).orElseThrow();
        String eventKey = UUID.randomUUID() + ":" + recipient.getId();
        notificationRepository.saveAndFlush(notification(recipient, incident, eventKey));

        assertThrows(DataIntegrityViolationException.class,
                () -> notificationRepository.saveAndFlush(notification(recipient, incident, eventKey)));
    }
}
