package com.incident.backend.service.email;

public record SimulatedEmailAttempt(Integer notificationId, int attemptNumber, SimulatedEmail email) { }
