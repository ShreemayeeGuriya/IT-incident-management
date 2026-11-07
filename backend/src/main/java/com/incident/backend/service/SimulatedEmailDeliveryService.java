package com.incident.backend.service;

import com.incident.backend.service.email.SimulatedEmailAttempt;
import com.incident.backend.service.email.SimulatedEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SimulatedEmailDeliveryService {
    public static final int MAX_ATTEMPTS = 3;
    private static final Logger log = LoggerFactory.getLogger(SimulatedEmailDeliveryService.class);

    private final NotificationDeliveryStateService stateService;
    private final SimulatedEmailSender emailSender;
    private final Set<Integer> inProgress = ConcurrentHashMap.newKeySet();

    public SimulatedEmailDeliveryService(NotificationDeliveryStateService stateService,
                                         SimulatedEmailSender emailSender) {
        this.stateService = stateService;
        this.emailSender = emailSender;
    }

    /** Sends outside the originating transaction, after durably reserving the bounded attempt count. */
    public void deliverNotification(Integer notificationId) {
        if (!inProgress.add(notificationId)) return;
        try {
            SimulatedEmailAttempt attempt;
            try {
                attempt = stateService.reserveAttempt(notificationId);
            } catch (RuntimeException failure) {
                log.error("Could not reserve simulated email attempt for notificationId={}", notificationId, failure);
                return;
            }
            if (attempt == null) return;

            RuntimeException deliveryFailure = null;
            try {
                emailSender.send(attempt.email());
            } catch (RuntimeException failure) {
                deliveryFailure = failure;
                log.warn("Simulated email attempt failed: notificationId={}, attempt={}",
                        notificationId, attempt.attemptNumber());
            }

            try {
                stateService.recordResult(notificationId, attempt.attemptNumber(), deliveryFailure);
            } catch (RuntimeException failure) {
                // The attempt reservation is already committed; report persistence failure without affecting the
                // originating operation. The retry scan can use the durable count and current delivery status.
                log.error("Could not save simulated email result for notificationId={}, attempt={}",
                        notificationId, attempt.attemptNumber(), failure);
            }
        } finally {
            inProgress.remove(notificationId);
        }
    }
}
