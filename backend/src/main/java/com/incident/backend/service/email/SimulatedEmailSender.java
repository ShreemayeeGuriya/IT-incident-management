package com.incident.backend.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Local email simulation only; configured addresses can be made to fail deterministically. */
@Component
public class SimulatedEmailSender {
    private static final Logger log = LoggerFactory.getLogger(SimulatedEmailSender.class);
    private final Set<String> failureRecipients;

    public SimulatedEmailSender(
            @Value("${app.notifications.email.simulate-failure-for:}") String failureRecipients) {
        this.failureRecipients = Arrays.stream(failureRecipients.split(","))
                .map(String::trim).filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    public void send(SimulatedEmail email) {
        if (failureRecipients.contains(email.recipientEmail().toLowerCase(Locale.ROOT))) {
            throw new SimulatedEmailException("Simulated email failure configured for this recipient");
        }
        log.info("Simulated email sent: notificationId={}, recipientUserId={}, subject={}",
                email.notificationId(), email.recipientUserId(), email.subject());
    }
}
