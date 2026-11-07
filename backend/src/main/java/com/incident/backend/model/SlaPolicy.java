package com.incident.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "sla_policies", uniqueConstraints =
        @UniqueConstraint(name = "uq_sla_policies_priority", columnNames = "priority"))
public class SlaPolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sla_policy_id")
    private Integer id;

    @Column(name = "policy_name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority;

    @Column(name = "response_time_minutes", nullable = false)
    private Integer responseTimeMinutes;

    @Column(name = "resolution_time_minutes", nullable = false)
    private Integer resolutionTimeMinutes;

    public Integer getId() { return id; }
    public String getName() { return name; }
    public Priority getPriority() { return priority; }
    public Integer getResponseTimeMinutes() { return responseTimeMinutes; }
    public Integer getResolutionTimeMinutes() { return resolutionTimeMinutes; }
}
