package com.incident.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id")
    private Incident incident;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "notification_type", nullable = false, length = 50)
    private String notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 30)
    private NotificationDeliveryStatus deliveryStatus = NotificationDeliveryStatus.PENDING;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "event_key", length = 160, unique = true)
    private String eventKey;

    @Column(name = "delivery_attempts", nullable = false)
    private Integer deliveryAttempts = 0;

    @Column(name = "last_delivery_error", columnDefinition = "text")
    private String lastDeliveryError;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (deliveryStatus == null) deliveryStatus = NotificationDeliveryStatus.PENDING;
        if (deliveryAttempts == null) deliveryAttempts = 0;
    }

    public Integer getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Incident getIncident() { return incident; }
    public void setIncident(Incident incident) { this.incident = incident; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getNotificationType() { return notificationType; }
    public void setNotificationType(String notificationType) { this.notificationType = notificationType; }
    public NotificationDeliveryStatus getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(NotificationDeliveryStatus deliveryStatus) { this.deliveryStatus = deliveryStatus; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }
    public Integer getDeliveryAttempts() { return deliveryAttempts; }
    public void setDeliveryAttempts(Integer deliveryAttempts) { this.deliveryAttempts = deliveryAttempts; }
    public String getLastDeliveryError() { return lastDeliveryError; }
    public void setLastDeliveryError(String lastDeliveryError) { this.lastDeliveryError = lastDeliveryError; }
}
