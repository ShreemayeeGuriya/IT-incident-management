package com.incident.backend.events;

import com.incident.backend.exception.ResourceNotFoundException;
import com.incident.backend.model.AvailabilityStatus;
import com.incident.backend.model.Incident;
import com.incident.backend.model.Role;
import com.incident.backend.model.Technician;
import com.incident.backend.model.User;
import com.incident.backend.model.SlaObjectiveStatus;
import com.incident.backend.repository.CommentRepository;
import com.incident.backend.repository.IncidentRepository;
import com.incident.backend.repository.TechnicianRepository;
import com.incident.backend.repository.UserRepository;
import com.incident.backend.service.NotificationService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class NotificationEventListener {
    private final NotificationService notificationService;
    private final IncidentRepository incidentRepository;
    private final TechnicianRepository technicianRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;

    public NotificationEventListener(NotificationService notificationService, IncidentRepository incidentRepository,
                                     TechnicianRepository technicianRepository, UserRepository userRepository,
                                     CommentRepository commentRepository) {
        this.notificationService = notificationService;
        this.incidentRepository = incidentRepository;
        this.technicianRepository = technicianRepository;
        this.userRepository = userRepository;
        this.commentRepository = commentRepository;
    }

    @EventListener
    public void onIncidentEvent(IncidentAuditEvent event) {
        Map<Integer, String> recipients = new LinkedHashMap<>();
        if (event instanceof IncidentCreatedEvent created) {
            addNewIncidentRecipients(created, recipients);
        } else if (event instanceof TechnicianAssignmentChangedEvent assignment) {
            addAssignmentRecipients(assignment, recipients);
        } else if (event instanceof IncidentStatusChangedEvent status) {
            addStatusRecipients(status, recipients);
        } else if (event instanceof CommentCreatedEvent comment) {
            addCommentRecipients(comment, recipients);
        } else if (event instanceof SlaObjectiveTransitionEvent sla
                && sla.newStatus() == SlaObjectiveStatus.BREACHED) {
            addSlaBreachRecipients(sla, recipients);
        } // IncidentUpdatedEvent intentionally creates no notification.

        if (!recipients.isEmpty()) notificationService.createNotifications(event, recipients);
    }

    private void addNewIncidentRecipients(IncidentCreatedEvent event, Map<Integer, String> recipients) {
        String message = "New incident #" + event.incidentId() + " has been created.";
        technicianRepository.findAllByUserRoleAndAvailabilityStatusNotOrderByIdAsc(
                        Role.TECHNICIAN, AvailabilityStatus.UNAVAILABLE)
                .forEach(technician -> add(recipients, technician.getUser(), message));
        userRepository.findByRoleIn(List.of(Role.ADMIN))
                .forEach(admin -> add(recipients, admin, message));
    }

    private void addAssignmentRecipients(TechnicianAssignmentChangedEvent event,
                                         Map<Integer, String> recipients) {
        Incident incident = incident(event.incidentId());
        add(recipients, incident.getReporter(), "The technician assignment changed for incident #"
                + event.incidentId() + ".");
        if (event.newTechnicianId() != null) {
            Technician technician = technicianRepository.findById(event.newTechnicianId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Technician not found: " + event.newTechnicianId()));
            add(recipients, technician.getUser(), "Incident #" + event.incidentId() + " was assigned to you.");
        } else if (event.oldTechnicianId() != null) {
            Technician previous = technicianRepository.findById(event.oldTechnicianId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Technician not found: " + event.oldTechnicianId()));
            add(recipients, previous.getUser(), "You were unassigned from incident #" + event.incidentId() + ".");
            commentRepository.findDistinctAuthorIdsByIncidentId(event.incidentId()).forEach(userId ->
                    add(recipients, userId, "The technician assignment changed for incident #"
                            + event.incidentId() + "."));
        }
    }

    private void addStatusRecipients(IncidentStatusChangedEvent event, Map<Integer, String> recipients) {
        Incident incident = incident(event.incidentId());
        String message = event.action().equals("INCIDENT_RESOLVED")
                ? "Incident #" + event.incidentId() + " has been resolved."
                : "Incident #" + event.incidentId() + " status changed to " + event.newStatus() + ".";
        add(recipients, incident.getReporter(), message);
        if (incident.getAssignedTechnician() != null) {
            add(recipients, incident.getAssignedTechnician().getUser(), message);
        }
    }

    private void addCommentRecipients(CommentCreatedEvent event, Map<Integer, String> recipients) {
        participants(event.incidentId()).forEach(userId -> add(recipients, userId,
                "A new comment was added to incident #" + event.incidentId() + "."));
    }

    private void addSlaBreachRecipients(SlaObjectiveTransitionEvent event,
                                         Map<Integer, String> recipients) {
        Incident incident = incident(event.incidentId());
        String message = "Incident #" + event.incidentId() + " has breached its "
                + event.objective().name().toLowerCase() + " SLA.";
        add(recipients, incident.getReporter(), message);
        if (incident.getAssignedTechnician() != null) {
            add(recipients, incident.getAssignedTechnician().getUser(), message);
        }
    }

    private Set<Integer> participants(Integer incidentId) {
        Incident incident = incident(incidentId);
        Set<Integer> participantIds = new LinkedHashSet<>();
        participantIds.add(incident.getReporter().getId());
        if (incident.getAssignedTechnician() != null) {
            participantIds.add(incident.getAssignedTechnician().getUser().getId());
        }
        participantIds.addAll(commentRepository.findDistinctAuthorIdsByIncidentId(incidentId));
        return participantIds;
    }

    private Incident incident(Integer incidentId) {
        return incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));
    }

    private void add(Map<Integer, String> recipients, User user, String message) {
        add(recipients, user.getId(), message);
    }

    private void add(Map<Integer, String> recipients, Integer userId, String message) {
        if (userId != null) recipients.putIfAbsent(userId, message);
    }
}
