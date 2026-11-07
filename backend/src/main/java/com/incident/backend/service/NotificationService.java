package com.incident.backend.service;

import com.incident.backend.dto.NotificationResponse;
import com.incident.backend.dto.PageResponse;
import com.incident.backend.dto.UnreadNotificationCountResponse;
import com.incident.backend.events.IncidentAuditEvent;
import com.incident.backend.events.NotificationCreatedEvent;
import com.incident.backend.exception.BadRequestException;
import com.incident.backend.exception.ResourceNotFoundException;
import com.incident.backend.model.Notification;
import com.incident.backend.model.User;
import com.incident.backend.repository.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationService(NotificationRepository notificationRepository,
                               ApplicationEventPublisher eventPublisher) {
        this.notificationRepository = notificationRepository;
        this.eventPublisher = eventPublisher;
    }

    /** Inserts one in-app notification per distinct recipient, atomically deduplicated by event and recipient. */
    public void createNotifications(IncidentAuditEvent event, Map<Integer, String> recipients) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("In-app notifications must be created inside the business transaction");
        }
        Set<Integer> distinctRecipientIds = new LinkedHashSet<>(recipients.keySet());
        for (Integer recipientId : distinctRecipientIds) {
            if (recipientId == null || recipientId.equals(event.actorUserId())) continue;
            String eventKey = event.eventId() + ":" + recipientId;
            int inserted = notificationRepository.insertIfEventKeyAbsent(recipientId, event.incidentId(),
                    recipients.get(recipientId), event.action(), eventKey);
            if (inserted == 1) {
                Integer notificationId = notificationRepository.findByEventKey(eventKey)
                        .orElseThrow(() -> new IllegalStateException(
                                "Inserted notification could not be read: " + eventKey))
                        .getId();
                eventPublisher.publishEvent(new NotificationCreatedEvent(notificationId));
            }
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listForUser(User current, int page, int size) {
        if (page < 0) throw new BadRequestException("page must be 0 or more");
        int pageSize = Math.min(Math.max(size, 1), 100);
        Page<Notification> notifications = notificationRepository
                .findAllByUserIdOrderByCreatedAtDescIdDesc(current.getId(), PageRequest.of(page, pageSize));
        return PageResponse.from(notifications.map(NotificationResponse::from));
    }

    @Transactional(readOnly = true)
    public UnreadNotificationCountResponse unreadCount(User current) {
        return new UnreadNotificationCountResponse(
                notificationRepository.countByUserIdAndIsReadFalse(current.getId()));
    }

    @Transactional
    public NotificationResponse markRead(Long notificationId, User current) {
        int id;
        try {
            id = Math.toIntExact(notificationId);
        } catch (ArithmeticException ex) {
            throw new ResourceNotFoundException("Notification not found: " + notificationId);
        }
        Notification notification = notificationRepository.findByIdAndUserId(id, current.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notification = notificationRepository.saveAndFlush(notification);
        }
        return NotificationResponse.from(notification);
    }
}
