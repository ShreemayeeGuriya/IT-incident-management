package com.incident.backend.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "incident_information_requests")
public class IncidentInformationRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "information_request_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requesting_technician_user_id", nullable = false)
    private User requestingTechnician;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_user_id", nullable = false)
    private User reporter;

    @Column(name = "request_message", nullable = false, length = 3000)
    private String requestMessage;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_status", nullable = false, length = 20)
    private InformationRequestStatus status = InformationRequestStatus.OPEN;

    @Column(name = "response_message", length = 3000)
    private String responseMessage;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @PrePersist
    void onCreate() {
        if (requestedAt == null) requestedAt = Instant.now();
        if (status == null) status = InformationRequestStatus.OPEN;
    }

    public Integer getId() { return id; }
    public Incident getIncident() { return incident; }
    public void setIncident(Incident incident) { this.incident = incident; }
    public User getRequestingTechnician() { return requestingTechnician; }
    public void setRequestingTechnician(User requestingTechnician) { this.requestingTechnician = requestingTechnician; }
    public User getReporter() { return reporter; }
    public void setReporter(User reporter) { this.reporter = reporter; }
    public String getRequestMessage() { return requestMessage; }
    public void setRequestMessage(String requestMessage) { this.requestMessage = requestMessage; }
    public Instant getRequestedAt() { return requestedAt; }
    public InformationRequestStatus getStatus() { return status; }
    public String getResponseMessage() { return responseMessage; }
    public Instant getRespondedAt() { return respondedAt; }

      public void respond(String message, Instant at) {
        this.responseMessage = message;
        this.respondedAt = at;
        this.status = InformationRequestStatus.RESPONDED;
    }
}
