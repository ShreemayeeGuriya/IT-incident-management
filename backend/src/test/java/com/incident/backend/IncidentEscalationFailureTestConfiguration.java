package com.incident.backend;

import com.incident.backend.events.NotificationCreatedEvent;
import com.incident.backend.repository.NotificationRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.atomic.AtomicBoolean;

@TestConfiguration(proxyBeanMethods = false)
class IncidentEscalationFailureTestConfiguration {
    static final AtomicBoolean FAIL_ESCALATION_NOTIFICATION = new AtomicBoolean();

    @Bean
    EscalationNotificationFailureProbe escalationNotificationFailureProbe(
            NotificationRepository notificationRepository) {
        return new EscalationNotificationFailureProbe(notificationRepository);
    }

    static final class EscalationNotificationFailureProbe {
        private final NotificationRepository notificationRepository;

        EscalationNotificationFailureProbe(NotificationRepository notificationRepository) {
            this.notificationRepository = notificationRepository;
        }

        @EventListener
        public void afterNotificationInsert(NotificationCreatedEvent event) {
            if (!FAIL_ESCALATION_NOTIFICATION.get()) return;
            boolean escalationNotification = notificationRepository.findById(event.notificationId())
                    .map(notification -> "INCIDENT_ESCALATED".equals(notification.getNotificationType()))
                    .orElse(false);
            if (escalationNotification && FAIL_ESCALATION_NOTIFICATION.compareAndSet(true, false)) {
                throw new IllegalStateException("simulated failure after escalation notification insert");
            }
        }
    }
}
