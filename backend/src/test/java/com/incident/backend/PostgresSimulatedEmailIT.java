package com.incident.backend;

import com.incident.backend.dto.IncidentCreateRequest;
import com.incident.backend.events.IncidentCreatedEvent;
import com.incident.backend.model.*;
import com.incident.backend.repository.*;
import com.incident.backend.service.IncidentService;
import com.incident.backend.service.NotificationService;
import com.incident.backend.service.SimulatedEmailDeliveryService;
import com.incident.backend.service.email.SimulatedEmail;
import com.incident.backend.service.email.SimulatedEmailException;
import com.incident.backend.service.email.SimulatedEmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "app.notifications.email.retry-interval-ms=3600000",
        "app.notifications.email.retry-initial-delay-ms=3600000"
})
@ActiveProfiles("postgres-it")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestDatabaseGuard.class)
@EnabledIfSystemProperty(named = "postgres.integration", matches = "true")
class PostgresSimulatedEmailIT {
    @Autowired NotificationService notificationService;
    @Autowired SimulatedEmailDeliveryService deliveryService;
    @Autowired IncidentService incidentService;
    @Autowired NotificationRepository notificationRepository;
    @Autowired UserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired AffectedServiceRepository serviceRepository;
    @Autowired SlaPolicyRepository slaPolicyRepository;
    @Autowired IncidentRepository incidentRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoSpyBean SimulatedEmailSender emailSender;
    @MockitoSpyBean com.incident.backend.service.NotificationDeliveryStateService deliveryStateServiceSpy;

    private final List<Integer> testUserIds = new ArrayList<>();
    private final List<String> testEventKeys = new ArrayList<>();
    private final List<Integer> committedIncidentIds = new ArrayList<>();

    private User createUser(Role role) {
        User user = new User();
        user.setName("Simulated email test recipient");
        user.setEmail("simulated-email-" + UUID.randomUUID() + "@example.test");
        user.setPasswordHash("test-hash");
        user.setRole(role);
        User saved = userRepository.saveAndFlush(user);
        testUserIds.add(saved.getId());
        return saved;
    }

    private String createNotificationAndCommit(User recipient) {
        UUID eventId = UUID.randomUUID();
        String key = eventId + ":" + recipient.getId();
        testEventKeys.add(key);
        IncidentCreatedEvent event = new IncidentCreatedEvent(eventId, null, null, Instant.now(),
                Map.of(), Map.of("status", "REPORTED"), "Incident created");
        transactionTemplate.executeWithoutResult(status -> notificationService.createNotifications(
                event, Map.of(recipient.getId(), "New incident notification for email delivery test.")));
        return key;
    }

    private Notification notification(String eventKey) {
        return notificationRepository.findByEventKey(eventKey).orElseThrow();
    }

    @Test
    void successfulDeliveryIsScheduledAfterCommitAndNotSentAgain() {
        User recipient = createUser(Role.EMPLOYEE);
        String eventKey = createNotificationAndCommit(recipient);
        Notification saved = notification(eventKey);

        assertEquals(NotificationDeliveryStatus.SENT, saved.getDeliveryStatus());
        assertEquals(1, saved.getDeliveryAttempts());
        assertNull(saved.getLastDeliveryError());
        ArgumentCaptor<SimulatedEmail> email = ArgumentCaptor.forClass(SimulatedEmail.class);
        verify(emailSender, times(1)).send(email.capture());
        assertEquals(recipient.getEmail(), email.getValue().recipientEmail());
        assertEquals(recipient.getId(), email.getValue().recipientUserId());
        assertTrue(email.getValue().subject().contains("New incident"));
        assertTrue(email.getValue().body().contains("New incident notification"));

        deliveryService.deliverNotification(saved.getId());
        verify(emailSender, times(1)).send(any());
        assertEquals(1, notification(eventKey).getDeliveryAttempts());
    }

    @Test
    void failedDeliveryPersistsErrorAndAttemptCount() {
        User recipient = createUser(Role.EMPLOYEE);
        doThrow(new SimulatedEmailException("deterministic test failure")).when(emailSender).send(any());

        String eventKey = createNotificationAndCommit(recipient);
        Notification failed = notification(eventKey);

        assertEquals(NotificationDeliveryStatus.FAILED, failed.getDeliveryStatus());
        assertEquals(1, failed.getDeliveryAttempts());
        assertTrue(failed.getLastDeliveryError().contains("deterministic test failure"));
        assertFalse(failed.isRead(), "delivery status is separate from in-app read state");
    }

    @Test
    void failedDeliveryCanSucceedOnRetry() {
        User recipient = createUser(Role.EMPLOYEE);
        doThrow(new SimulatedEmailException("temporary failure")).doNothing()
                .when(emailSender).send(any());
        String eventKey = createNotificationAndCommit(recipient);
        Notification initiallyFailed = notification(eventKey);
        assertEquals(NotificationDeliveryStatus.FAILED, initiallyFailed.getDeliveryStatus());
        assertEquals(1, initiallyFailed.getDeliveryAttempts());
        assertTrue(notificationRepository.findRetryableIds(NotificationDeliveryStatus.PENDING,
                        NotificationDeliveryStatus.FAILED, SimulatedEmailDeliveryService.MAX_ATTEMPTS,
                        PageRequest.of(0, 100)).contains(initiallyFailed.getId()));

        deliveryService.deliverNotification(initiallyFailed.getId());
        Notification sent = notification(eventKey);
        assertEquals(NotificationDeliveryStatus.SENT, sent.getDeliveryStatus());
        assertEquals(2, sent.getDeliveryAttempts());
        assertNull(sent.getLastDeliveryError());
        verify(emailSender, times(2)).send(any());
    }

    @Test
    void retriesStopAtThreeTotalAttempts() {
        User recipient = createUser(Role.EMPLOYEE);
        doThrow(new SimulatedEmailException("persistent test failure")).when(emailSender).send(any());
        String eventKey = createNotificationAndCommit(recipient);
        Integer id = notification(eventKey).getId();

        deliveryService.deliverNotification(id);
        deliveryService.deliverNotification(id);
        deliveryService.deliverNotification(id);
        deliveryService.deliverNotification(id);

        Notification failed = notification(eventKey);
        assertEquals(NotificationDeliveryStatus.FAILED, failed.getDeliveryStatus());
        assertEquals(3, failed.getDeliveryAttempts());
        verify(emailSender, times(3)).send(any());
    }

    @Test
    void duplicateEventProcessingCreatesOneNotificationAndOneEmail() {
        User recipient = createUser(Role.EMPLOYEE);
        UUID eventId = UUID.randomUUID();
        String eventKey = eventId + ":" + recipient.getId();
        testEventKeys.add(eventKey);
        IncidentCreatedEvent event = new IncidentCreatedEvent(eventId, null, null, Instant.now(),
                Map.of(), Map.of("status", "REPORTED"), "Incident created");

        transactionTemplate.executeWithoutResult(status -> notificationService.createNotifications(
                event, Map.of(recipient.getId(), "One in-app notification.")));
        transactionTemplate.executeWithoutResult(status -> notificationService.createNotifications(
                event, Map.of(recipient.getId(), "One in-app notification.")));

        assertEquals(1, notificationRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(
                        recipient.getId(), PageRequest.of(0, 100)).getContent().stream()
                .filter(n -> eventKey.equals(n.getEventKey())).count());
        verify(emailSender, times(1)).send(any());
    }

    @Test
    void deliveryStatePersistenceFailureDoesNotEscapeAfterCommitOrRemoveNotification() {
        User recipient = createUser(Role.EMPLOYEE);
        org.mockito.Mockito.doThrow(new IllegalStateException("forced delivery state write failure"))
                .when(deliveryStateServiceSpy).recordResult(any(), org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.nullable(RuntimeException.class));

        String eventKey = createNotificationAndCommit(recipient);
        Notification pending = notification(eventKey);

        assertEquals(NotificationDeliveryStatus.PENDING, pending.getDeliveryStatus());
        assertEquals(1, pending.getDeliveryAttempts(), "the reserved attempt is durable before delivery");
        assertTrue(notificationRepository.existsByEventKey(eventKey));
        verify(emailSender, times(1)).send(any());
    }

    @Test
    void emailFailureDoesNotRollbackCommittedIncidentOrInAppNotification() {
        User reporter = createUser(Role.EMPLOYEE);
        User adminRecipient = createUser(Role.ADMIN);
        doThrow(new SimulatedEmailException("email failed after business commit")).when(emailSender).send(any());
        IncidentCreateRequest request = incidentRequest();

        var created = transactionTemplate.execute(status -> incidentService.create(request, reporter));
        committedIncidentIds.add(Math.toIntExact(created.id()));

        assertTrue(incidentRepository.existsById(Math.toIntExact(created.id())));
        assertTrue(auditLogRepository.countByIncidentId(Math.toIntExact(created.id())) > 0,
                "Stage 2 audit records must commit with the incident");
        Notification inApp = notificationRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(
                        adminRecipient.getId(), PageRequest.of(0, 100)).getContent().stream()
                .filter(n -> n.getIncident() != null && n.getIncident().getId().equals(created.id().intValue()))
                .findFirst().orElseThrow();
        assertEquals(NotificationDeliveryStatus.FAILED, inApp.getDeliveryStatus());
        assertEquals(1, inApp.getDeliveryAttempts());
    }

    private IncidentCreateRequest incidentRequest() {
        Category category = categoryRepository.findAll(Sort.by("id")).get(0);
        AffectedService service = serviceRepository.findAll(Sort.by("id")).get(0);
        SlaPolicy policy = slaPolicyRepository.findByPriority(Priority.P3).orElseThrow();
        return new IncidentCreateRequest("Email failure must not roll back incident", category.getId(),
                service.getId(), policy.getId(), Impact.MEDIUM, Urgency.MEDIUM,
                BusinessCriticality.MEDIUM, policy.getPriority());
    }

    @AfterEach
    void cleanUpCommittedTestData() {
        transactionTemplate.executeWithoutResult(status -> {
            for (Integer incidentId : committedIncidentIds) {
                jdbcTemplate.update("DELETE FROM comments WHERE incident_id = ?", incidentId);
                jdbcTemplate.update("DELETE FROM audit_logs WHERE incident_id = ?", incidentId);
                jdbcTemplate.update("DELETE FROM notifications WHERE incident_id = ?", incidentId);
                jdbcTemplate.update("DELETE FROM incidents WHERE incident_id = ?", incidentId);
            }
            for (String eventKey : testEventKeys) {
                jdbcTemplate.update("DELETE FROM notifications WHERE event_key = ?", eventKey);
            }
            for (Integer userId : testUserIds) userRepository.deleteById(userId);
            userRepository.flush();
        });
    }
}
