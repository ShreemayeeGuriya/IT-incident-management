package com.incident.backend.service;

import com.incident.backend.model.NotificationDeliveryStatus;
import com.incident.backend.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificationRetryScheduler {
    private static final Logger log = LoggerFactory.getLogger(NotificationRetryScheduler.class);
    private final NotificationRepository notificationRepository;
    private final SimulatedEmailDeliveryService deliveryService;

    public NotificationRetryScheduler(NotificationRepository notificationRepository,
                                      SimulatedEmailDeliveryService deliveryService) {
        this.notificationRepository = notificationRepository;
        this.deliveryService = deliveryService;
    }

    @Scheduled(fixedDelayString = "${app.notifications.email.retry-interval-ms:60000}",
            initialDelayString = "${app.notifications.email.retry-initial-delay-ms:60000}")
    public void retryFailedDeliveries() {
        List<Integer> notificationIds;
        try {
            notificationIds = notificationRepository.findRetryableIds(
                    NotificationDeliveryStatus.PENDING, NotificationDeliveryStatus.FAILED,
                    SimulatedEmailDeliveryService.MAX_ATTEMPTS, PageRequest.of(0, 100));
        } catch (RuntimeException failure) {
            log.error("Could not load simulated email retry candidates", failure);
            return;
        }
        for (Integer id : notificationIds) {
            try {
                deliveryService.deliverNotification(id);
            } catch (RuntimeException failure) {
                log.error("Could not retry simulated email for notificationId={}", id, failure);
            }
        }
    }
}
