package com.incident.backend;

import com.incident.backend.dto.IncidentCreateRequest;
import com.incident.backend.dto.IncidentUpdateRequest;
import com.incident.backend.dto.StateChangeRequest;
import com.incident.backend.events.IncidentCreatedEvent;
import com.incident.backend.model.*;
import com.incident.backend.repository.*;
import com.incident.backend.security.JwtService;
import com.incident.backend.service.IncidentService;
import com.incident.backend.service.IncidentWorkflowService;
import com.incident.backend.service.CommentService;
import com.incident.backend.service.NotificationService;
import com.incident.backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("postgres-it")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestDatabaseGuard.class)
@AutoConfigureMockMvc
@Transactional
@EnabledIfSystemProperty(named = "postgres.integration", matches = "true")
class PostgresNotificationFlowIT {
    @Autowired NotificationService notificationService;
    @Autowired IncidentService incidentService;
    @Autowired IncidentWorkflowService workflowService;
    @Autowired CommentService commentService;
    @Autowired UserService userService;
    @Autowired NotificationRepository notificationRepository;
    @Autowired UserRepository userRepository;
    @Autowired TechnicianRepository technicianRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired AffectedServiceRepository serviceRepository;
    @Autowired SlaPolicyRepository slaPolicyRepository;
    @Autowired IncidentRepository incidentRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired CommentRepository commentRepository;
    @Autowired JwtService jwtService;
    @Autowired MockMvc mvc;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoSpyBean NotificationService notificationServiceSpy;

    private User user(Role role) {
        User user = new User();
        user.setName("Notification flow test");
        user.setEmail("notification-flow-" + UUID.randomUUID() + "@example.test");
        user.setPasswordHash("test-hash");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    private User technician(User admin) {
        User user = user(Role.EMPLOYEE);
        userService.updateRole(user.getId().longValue(), Role.TECHNICIAN, admin);
        return userRepository.findById(user.getId()).orElseThrow();
    }

    private IncidentCreateRequest request() {
        Category category = categoryRepository.findAll(Sort.by("id")).get(0);
        AffectedService service = serviceRepository.findAll(Sort.by("id")).get(0);
        SlaPolicy policy = slaPolicyRepository.findByPriority(Priority.P3).orElseThrow();
        return new IncidentCreateRequest("Rollback-safe notification flow test", category.getId(),
                service.getId(), policy.getId(), Impact.MEDIUM, Urgency.MEDIUM,
                BusinessCriticality.MEDIUM, policy.getPriority());
    }

    private Incident createIncident(User reporter) {
        return incidentRepository.findById(Math.toIntExact(
                incidentService.create(request(), reporter).id())).orElseThrow();
    }

    private List<Notification> notifications(User user) {
        return notificationRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(
                user.getId(), PageRequest.of(0, 100)).getContent();
    }

    private long eventCount(User user, Integer incidentId, String type) {
        return notifications(user).stream().filter(n -> n.getIncident() != null
                && n.getIncident().getId().equals(incidentId)
                && n.getNotificationType().equals(type)).count();
    }

    @Test
    @Rollback
    void newIncidentNotifiesActiveTechniciansAndAdminsExceptActorAndKeepsAudit() {
        User actorAdmin = user(Role.ADMIN);
        User otherAdmin = user(Role.ADMIN);
        User techUser = technician(actorAdmin);
        User unavailableTech = technician(actorAdmin);
        Integer unavailableTechnicianId = technicianRepository.findByUserId(unavailableTech.getId())
                .orElseThrow().getId();
        jdbcTemplate.update("UPDATE technicians SET availability_status = 'UNAVAILABLE' WHERE technician_id = ?",
                unavailableTechnicianId);
        Incident incident = createIncident(actorAdmin);

        assertEquals(1, eventCount(otherAdmin, incident.getId(), "INCIDENT_CREATED"));
        assertEquals(1, eventCount(techUser, incident.getId(), "INCIDENT_CREATED"));
        assertEquals(0, eventCount(unavailableTech, incident.getId(), "INCIDENT_CREATED"));
        assertEquals(0, eventCount(actorAdmin, incident.getId(), "INCIDENT_CREATED"));
        assertTrue(auditLogRepository.countByIncidentId(incident.getId()) >= 1,
                "Stage 2 audit listener must continue writing in the same transaction");
    }

    @Test
    @Rollback
    void assignmentAndStatusNotificationsReachParticipantsAndExcludeActor() {
        User reporter = user(Role.EMPLOYEE);
        User adminActor = user(Role.ADMIN);
        User assignee = technician(adminActor);
        Incident incident = createIncident(reporter);

        incidentService.update(incident.getId().longValue(),
                new IncidentUpdateRequest("Routine description edit", null, null,
                        null, null, null, null, null, null, null, null), reporter);
        assertEquals(0, eventCount(reporter, incident.getId(), "INCIDENT_UPDATED"));

        Integer technicianId = technicianRepository.findByUserId(assignee.getId()).orElseThrow().getId();
        workflowService.assign(incident.getId().longValue(), technicianId.longValue(), adminActor);
        assertEquals(1, eventCount(reporter, incident.getId(), "TECHNICIAN_ASSIGNMENT_CHANGED"));
        assertEquals(1, eventCount(assignee, incident.getId(), "TECHNICIAN_ASSIGNMENT_CHANGED"));
        assertEquals(0, eventCount(adminActor, incident.getId(), "TECHNICIAN_ASSIGNMENT_CHANGED"));

        // Walk the lifecycle up to RESOLVED; skipping steps is no longer allowed.
        for (IncidentStatus step : List.of(IncidentStatus.INVESTIGATING)) {
            workflowService.changeState(incident.getId().longValue(), new StateChangeRequest(step), adminActor);
        }
        incidentService.update(incident.getId().longValue(), new IncidentUpdateRequest(null, null, null, null,
                null, null, null, null, null, "Restarted the affected service", null), adminActor);
        workflowService.changeState(incident.getId().longValue(),
                new StateChangeRequest(IncidentStatus.RESOLVED), adminActor);
        assertEquals(1, eventCount(reporter, incident.getId(), "INCIDENT_RESOLVED"));
        assertEquals(1, eventCount(assignee, incident.getId(), "INCIDENT_RESOLVED"));
        assertEquals(0, eventCount(adminActor, incident.getId(), "INCIDENT_RESOLVED"));
    }

    @Test
    @Rollback
    void unassignmentNotifiesReporterFormerAssigneeAndOtherParticipantsExceptActor() {
        User reporter = user(Role.EMPLOYEE);
        User adminActor = user(Role.ADMIN);
        User formerAssignee = technician(adminActor);
        User priorCommenter = technician(adminActor);
        Incident incident = createIncident(reporter);
        Integer formerTechnicianId = technicianRepository.findByUserId(formerAssignee.getId())
                .orElseThrow().getId();
        workflowService.assign(incident.getId().longValue(), formerTechnicianId.longValue(), adminActor);
        commentService.add(incident.getId().longValue(), "Prior participant comment", priorCommenter);

        workflowService.assign(incident.getId().longValue(), null, adminActor);

        assertEquals(2, eventCount(reporter, incident.getId(), "TECHNICIAN_ASSIGNMENT_CHANGED"));
        assertEquals(2, eventCount(formerAssignee, incident.getId(), "TECHNICIAN_ASSIGNMENT_CHANGED"));
        assertEquals(1, eventCount(priorCommenter, incident.getId(), "TECHNICIAN_ASSIGNMENT_CHANGED"));
        assertEquals(0, eventCount(adminActor, incident.getId(), "TECHNICIAN_ASSIGNMENT_CHANGED"));
        assertTrue(notifications(formerAssignee).stream().anyMatch(notification ->
                notification.getIncident().getId().equals(incident.getId())
                        && notification.getMessage().contains("unassigned")));
    }

    @Test
    @Rollback
    void duplicateParticipantRolesProduceOneNotificationAndCommentsNotifyOtherParticipants() {
        User admin = user(Role.ADMIN);
        User reporterAndAssignee = technician(admin);
        Incident incident = createIncident(reporterAndAssignee);

        Integer sameTechnicianId = technicianRepository.findByUserId(reporterAndAssignee.getId())
                .orElseThrow().getId();
        workflowService.assign(incident.getId().longValue(), sameTechnicianId.longValue(), admin);
        assertEquals(1, eventCount(reporterAndAssignee, incident.getId(), "TECHNICIAN_ASSIGNMENT_CHANGED"),
                "reporter and assignee are one recipient when they are the same user");

        User otherParticipant = technician(admin);
        // A technician can comment, and becomes an incident participant for later comments.
        commentService.add(incident.getId().longValue(), "First private comment", otherParticipant);
        long beforeReporterComment = eventCount(otherParticipant, incident.getId(), "COMMENT_CREATED");
        long reporterCountBeforeOwnComment = eventCount(reporterAndAssignee, incident.getId(), "COMMENT_CREATED");
        commentService.add(incident.getId().longValue(), "Reporter comment", reporterAndAssignee);

        assertEquals(beforeReporterComment + 1,
                eventCount(otherParticipant, incident.getId(), "COMMENT_CREATED"));
        assertEquals(0, eventCount(admin, incident.getId(), "COMMENT_CREATED"));
        assertEquals(reporterCountBeforeOwnComment,
                eventCount(reporterAndAssignee, incident.getId(), "COMMENT_CREATED"),
                "comment author must not receive their own notification");
    }

    @Test
    @Rollback
    void repeatedEventRecipientPairIsAtomicallyDeduplicated() {
        User recipient = user(Role.EMPLOYEE);
        Incident incident = createIncident(recipient);
        UUID eventId = UUID.randomUUID();
        IncidentCreatedEvent event = new IncidentCreatedEvent(eventId, incident.getId(), null,
                Instant.now(), Map.of(), Map.of("status", "REPORTED"), "test event");
        Map<Integer, String> recipients = Map.of(recipient.getId(), "Test notification");

        notificationService.createNotifications(event, recipients);
        notificationService.createNotifications(event, recipients);

        assertTrue(notificationRepository.existsByEventKey(eventId + ":" + recipient.getId()));
        assertEquals(1, notifications(recipient).stream()
                .filter(n -> (eventId + ":" + recipient.getId()).equals(n.getEventKey())).count());
    }

    @Test
    @Rollback
    void notificationsArePagedOwnedAndMarkReadSetsReadAt() throws Exception {
        User owner = user(Role.EMPLOYEE);
        User other = user(Role.EMPLOYEE);
        Notification first = saveNotification(owner, "api-test-1-" + UUID.randomUUID());
        Notification second = saveNotification(owner, "api-test-2-" + UUID.randomUUID());
        Notification third = saveNotification(owner, "api-test-3-" + UUID.randomUUID());
        Notification foreign = saveNotification(other, "api-test-foreign-" + UUID.randomUUID());
        String ownerToken = jwtService.generateToken(owner);
        String otherToken = jwtService.generateToken(other);

        mvc.perform(get("/api/notifications?page=0&size=2").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(third.getId()))
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3));
        mvc.perform(patch("/api/notifications/{id}/read", first.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true))
                .andExpect(jsonPath("$.readAt").isNotEmpty());
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2));
        mvc.perform(patch("/api/notifications/{id}/read", foreign.getId())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(foreign.getId()));
        assertNotEquals(second.getId(), third.getId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void notificationPersistenceFailureRollsBackIncidentCreation() {
        User reporter = transactionTemplate.execute(status -> user(Role.EMPLOYEE));
        User adminRecipient = transactionTemplate.execute(status -> user(Role.ADMIN));
        long incidentCountBefore = incidentRepository.count();
        try {
            doThrow(new IllegalStateException("forced notification persistence failure"))
                    .when(notificationServiceSpy).createNotifications(any(), anyMap());
            assertThrows(IllegalStateException.class, () -> transactionTemplate.execute(status -> {
                incidentService.create(request(), reporter);
                return null;
            }));
            assertEquals(incidentCountBefore, incidentRepository.count());
        } finally {
            transactionTemplate.executeWithoutResult(status -> userRepository.deleteById(reporter.getId()));
            transactionTemplate.executeWithoutResult(status -> userRepository.deleteById(adminRecipient.getId()));
        }
    }

    private Notification saveNotification(User recipient, String eventKey) {
        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setMessage("Test in-app notification");
        notification.setNotificationType("TEST");
        notification.setEventKey(eventKey);
        return notificationRepository.saveAndFlush(notification);
    }
}
