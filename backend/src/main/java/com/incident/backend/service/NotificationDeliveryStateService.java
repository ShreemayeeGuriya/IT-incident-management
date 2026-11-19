package com.incident.backend.service;

import com.incident.backend.model.Notification;
import com.incident.backend.model.NotificationDeliveryStatus;
import com.incident.backend.repository.NotificationRepository;
import com.incident.backend.service.email.SimulatedEmail;
import com.incident.backend.service.email.SimulatedEmailAttempt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDeliveryStateService {
    private final NotificationRepository notificationRepository;

    public NotificationDeliveryStateService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SimulatedEmailAttempt reserveAttempt(Integer notificationId) {
        Notification notification = notificationRepository.findByIdForDelivery(notificationId).orElse(null);
        if (notification == null || notification.getDeliveryStatus() == NotificationDeliveryStatus.SENT) return null;

        int previousAttempts = notification.getDeliveryAttempts() == null ? 0 : notification.getDeliveryAttempts();
        if (previousAttempts >= SimulatedEmailDeliveryService.MAX_ATTEMPTS) {
            notification.setDeliveryStatus(NotificationDeliveryStatus.FAILED);
            notificationRepository.saveAndFlush(notification);
            return null;
        }

        int attemptNumber = previousAttempts + 1;
        notification.setDeliveryAttempts(attemptNumber);
        notificationRepository.saveAndFlush(notification);
        return new SimulatedEmailAttempt(notificationId, attemptNumber, emailFor(notification));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordResult(Integer notificationId, int attemptNumber, RuntimeException failure) {
        Notification notification = notificationRepository.findByIdForDelivery(notificationId).orElse(null);
        if (notification == null || notification.getDeliveryStatus() == NotificationDeliveryStatus.SENT
                || notification.getDeliveryAttempts() != attemptNumber) return;

        if (failure == null) {
            notification.setDeliveryStatus(NotificationDeliveryStatus.SENT);
            notification.setLastDeliveryError(null);
        } else {
            notification.setDeliveryStatus(NotificationDeliveryStatus.FAILED);
            notification.setLastDeliveryError(errorMessage(failure));
        }
        notificationRepository.saveAndFlush(notification);
    }

    private SimulatedEmail emailFor(Notification notification) {
        String incidentNumber = notification.getIncident() == null ? null
                : "#" + notification.getIncident().getId();
        String subjectIncident = incidentNumber == null ? "incident notification" : "incident " + incidentNumber;
        String subject = switch (notification.getNotificationType()) {
            case "INCIDENT_CREATED" -> "Radar ITSM: New " + subjectIncident;
            case "TECHNICIAN_ASSIGNMENT_CHANGED" -> "Radar ITSM: Assignment update for " + subjectIncident;
            case "INCIDENT_RESOLVED" -> "Radar ITSM: " + subjectIncident + " resolved";
            case "INCIDENT_CLOSED" -> "Radar ITSM: " + subjectIncident + " closed";
            case "INCIDENT_STATUS_CHANGED" -> "Radar ITSM: Status update for " + subjectIncident;
            case "COMMENT_CREATED" -> "Radar ITSM: New comment on " + subjectIncident;
            case "SLA_RESPONSE_BREACHED" -> "Radar ITSM: Response SLA breached for " + subjectIncident;
            case "SLA_RESOLUTION_BREACHED" -> "Radar ITSM: Resolution SLA breached for " + subjectIncident;
            case "INCIDENT_ESCALATED" -> "Radar ITSM: Escalation for " + subjectIncident;
            default -> "Radar ITSM: Incident notification";
        };
        var recipient = notification.getUser();
        String body = "Hello " + recipient.getName() + ",\n\n" + notification.getMessage()
                + "\n\nSign in to Radar ITSM to view the incident details.";
        return new SimulatedEmail(notification.getId(), recipient.getId(), recipient.getName(),
                recipient.getEmail(), subject, body);
    }

    private String errorMessage(RuntimeException failure) {
        String message = failure.getMessage();
        String value = failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
