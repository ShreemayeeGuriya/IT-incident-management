package com.incident.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** Idempotency record for an incident escalation level. */
@Entity
@Table(name = "incident_escalations",
        uniqueConstraints = @UniqueConstraint(name = "uq_incident_escalations_incident_level",
                columnNames = {"incident_id", "escalation_level"}),
        indexes = @Index(name = "idx_incident_escalations_incident_created",
                columnList = "incident_id, created_at"))
public class IncidentEscalation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "escalation_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(name = "escalation_level", nullable = false)
    private Integer escalationLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_objective", nullable = false, length = 20)
    private SlaObjective triggerObjective;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IncidentEscalation() { }

    public Integer getId() { return id; }
    public Incident getIncident() { return incident; }
    public Integer getEscalationLevel() { return escalationLevel; }
    public SlaObjective getTriggerObjective() { return triggerObjective; }
    public Instant getCreatedAt() { return createdAt; }
}
