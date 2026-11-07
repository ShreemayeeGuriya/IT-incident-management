package com.incident.backend.events;

/** Published only after a new in-app notification row has been inserted. */
public record NotificationCreatedEvent(Integer notificationId) { }
