package com.incident.backend.dto;

import com.incident.backend.model.Notification;
import java.time.Instant;

public record NotificationResponse(Long id, Long incidentId, String message, String notificationType,
                                   boolean isRead, Instant createdAt, Instant readAt) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId().longValue(),
                notification.getIncident() == null ? null : notification.getIncident().getId().longValue(),
                notification.getMessage(), notification.getNotificationType(), notification.isRead(),
                notification.getCreatedAt(), notification.getReadAt());
    }
}
