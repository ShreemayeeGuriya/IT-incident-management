package com.incident.backend.service.email;

public record SimulatedEmail(Integer notificationId, Integer recipientUserId, String recipientName,
                             String recipientEmail, String subject, String body) { }
