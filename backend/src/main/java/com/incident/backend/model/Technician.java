package com.incident.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "technicians")
public class Technician {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "technician_id")
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "max_workload", nullable = false)
    private Integer maxWorkload = 5;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", nullable = false, length = 30)
    private AvailabilityStatus availabilityStatus = AvailabilityStatus.AVAILABLE;

    @Column(name = "experience_level", length = 30)
    private String experienceLevel;

    protected Technician() { }

    public Technician(User user) { this.user = user; }

    public Integer getId() { return id; }
    public User getUser() { return user; }
    public Integer getMaxWorkload() { return maxWorkload; }
    public AvailabilityStatus getAvailabilityStatus() { return availabilityStatus; }
    public String getExperienceLevel() { return experienceLevel; }
}
