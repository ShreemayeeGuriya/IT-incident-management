package com.incident.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;

    protected AuditLog() { }

    public AuditLog(Incident incident, User actor, String action, String oldValue,
                    String newValue, String description, Instant timestamp) {
        this.incident = incident;
        this.actor = actor;
        this.action = action;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.description = description;
        this.timestamp = timestamp;
    }

    public Integer getId() { return id; }
    public Incident getIncident() { return incident; }
    public User getActor() { return actor; }
    public String getAction() { return action; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public String getDescription() { return description; }
    public Instant getTimestamp() { return timestamp; }
}
