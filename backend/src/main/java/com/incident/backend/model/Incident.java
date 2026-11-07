package com.incident.backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "incidents")
public class Incident {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "incident_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_technician_id")
    private Technician assignedTechnician;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false)
    private AffectedService service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sla_policy_id")
    private SlaPolicy slaPolicy;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Impact impact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Urgency urgency;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_criticality", nullable = false, length = 20)
    private BusinessCriticality businessCriticality;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private IncidentStatus status = IncidentStatus.REPORTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "sla_status", nullable = false, length = 30)
    private SlaStatus slaStatus = SlaStatus.NOT_STARTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_sla_status", nullable = false, length = 30)
    private SlaObjectiveStatus responseSlaStatus = SlaObjectiveStatus.NOT_STARTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_sla_status", nullable = false, length = 30)
    private SlaObjectiveStatus resolutionSlaStatus = SlaObjectiveStatus.NOT_STARTED;

    @Column(name = "investigation_details", columnDefinition = "text")
    private String investigationDetails;

    @Column(name = "resolution_details", columnDefinition = "text")
    private String resolutionDetails;

    @Column(name = "closure_details", columnDefinition = "text")
    private String closureDetails;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "first_response_at")
    private Instant firstResponseAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "response_deadline")
    private Instant responseDeadline;

    @Column(name = "resolution_deadline")
    private Instant resolutionDeadline;

    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<Comment> comments = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = IncidentStatus.REPORTED;
        if (slaStatus == null) slaStatus = SlaStatus.NOT_STARTED;
        if (responseSlaStatus == null) responseSlaStatus = SlaObjectiveStatus.NOT_STARTED;
        if (resolutionSlaStatus == null) resolutionSlaStatus = SlaObjectiveStatus.NOT_STARTED;
    }

    public Integer getId() { return id; }
    public User getReporter() { return reporter; }
    public void setReporter(User reporter) { this.reporter = reporter; }
    public Technician getAssignedTechnician() { return assignedTechnician; }
    public void setAssignedTechnician(Technician assignedTechnician) { this.assignedTechnician = assignedTechnician; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public AffectedService getService() { return service; }
    public void setService(AffectedService service) { this.service = service; }
    public SlaPolicy getSlaPolicy() { return slaPolicy; }
    public void setSlaPolicy(SlaPolicy slaPolicy) { this.slaPolicy = slaPolicy; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Impact getImpact() { return impact; }
    public void setImpact(Impact impact) { this.impact = impact; }
    public Urgency getUrgency() { return urgency; }
    public void setUrgency(Urgency urgency) { this.urgency = urgency; }
    public BusinessCriticality getBusinessCriticality() { return businessCriticality; }
    public void setBusinessCriticality(BusinessCriticality businessCriticality) { this.businessCriticality = businessCriticality; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public IncidentStatus getStatus() { return status; }
    public void setStatus(IncidentStatus status) { this.status = status; }
    public SlaStatus getSlaStatus() { return slaStatus; }
    public void setSlaStatus(SlaStatus slaStatus) { this.slaStatus = slaStatus; }
    public SlaObjectiveStatus getResponseSlaStatus() { return responseSlaStatus; }
    public void setResponseSlaStatus(SlaObjectiveStatus responseSlaStatus) { this.responseSlaStatus = responseSlaStatus; }
    public SlaObjectiveStatus getResolutionSlaStatus() { return resolutionSlaStatus; }
    public void setResolutionSlaStatus(SlaObjectiveStatus resolutionSlaStatus) { this.resolutionSlaStatus = resolutionSlaStatus; }
    public String getInvestigationDetails() { return investigationDetails; }
    public void setInvestigationDetails(String investigationDetails) { this.investigationDetails = investigationDetails; }
    public String getResolutionDetails() { return resolutionDetails; }
    public void setResolutionDetails(String resolutionDetails) { this.resolutionDetails = resolutionDetails; }
    public String getClosureDetails() { return closureDetails; }
    public void setClosureDetails(String closureDetails) { this.closureDetails = closureDetails; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFirstResponseAt() { return firstResponseAt; }
    public void setFirstResponseAt(Instant firstResponseAt) { this.firstResponseAt = firstResponseAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public Instant getResponseDeadline() { return responseDeadline; }
    public void setResponseDeadline(Instant responseDeadline) { this.responseDeadline = responseDeadline; }
    public Instant getResolutionDeadline() { return resolutionDeadline; }
    public void setResolutionDeadline(Instant resolutionDeadline) { this.resolutionDeadline = resolutionDeadline; }
    public List<Comment> getComments() { return comments; }
}
