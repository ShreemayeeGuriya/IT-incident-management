package com.incident.backend.events;

import com.incident.backend.service.SimulatedEmailDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SimulatedEmailAfterCommitListener {
    private static final Logger log = LoggerFactory.getLogger(SimulatedEmailAfterCommitListener.class);
    private final SimulatedEmailDeliveryService deliveryService;

    public SimulatedEmailAfterCommitListener(SimulatedEmailDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliverAfterCommit(NotificationCreatedEvent event) {
        try {
            deliveryService.deliverNotification(event.notificationId());
        } catch (RuntimeException failure) {
            // The business transaction is already committed; a delivery-state outage must not surface as its rollback.
            log.error("Could not process simulated email for notificationId={}", event.notificationId(), failure);
        }
    }
}
