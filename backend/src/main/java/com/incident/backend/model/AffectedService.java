package com.incident.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "services")
public class AffectedService {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "service_id")
    private Integer id;

    @Column(name = "service_name", nullable = false, unique = true, length = 150)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    public Integer getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
}
