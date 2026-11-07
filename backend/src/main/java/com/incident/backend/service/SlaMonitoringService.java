package com.incident.backend.service;

import com.incident.backend.model.Incident;
import com.incident.backend.model.SlaObjectiveStatus;
import com.incident.backend.model.SlaStatus;
import com.incident.backend.model.SlaObjective;
import com.incident.backend.events.SlaObjectiveTransitionEvent;
import com.incident.backend.repository.IncidentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Objects;

@Service
public class SlaMonitoringService {
    private final IncidentRepository incidentRepository;
    private final SlaService slaService;
    private final ApplicationEventPublisher eventPublisher;
    private final double approachingThreshold;

    public SlaMonitoringService(IncidentRepository incidentRepository, SlaService slaService,
                                ApplicationEventPublisher eventPublisher,
                                @Value("${app.sla.approaching-threshold:0.8}") double approachingThreshold) {
        if (!Double.isFinite(approachingThreshold)
                || approachingThreshold <= 0.0 || approachingThreshold >= 1.0) {
            throw new IllegalArgumentException("app.sla.approaching-threshold must be between 0 and 1");
        }
        this.incidentRepository = incidentRepository;
        this.slaService = slaService;
        this.eventPublisher = eventPublisher;
        this.approachingThreshold = approachingThreshold;
    }

    /** Loads and updates one incident under a row lock in the caller's transaction. */
    @Transactional
    public Optional<Evaluation> evaluateIncident(Integer incidentId, Instant now) {
        Incident incident = incidentRepository.findByIdForUpdate(incidentId).orElse(null);
        if (incident == null) return Optional.empty();

        SlaObjectiveStatus oldResponse = incident.getResponseSlaStatus();
        SlaObjectiveStatus oldResolution = incident.getResolutionSlaStatus();
        SlaStatus oldSummary = incident.getSlaStatus();

        SlaObjectiveStatus response = slaService.evaluateResponseStatus(oldResponse,
                incident.getCreatedAt(), incident.getResponseDeadline(), incident.getFirstResponseAt(),
                incident.getResolvedAt(), incident.getClosedAt(), now, approachingThreshold);
        SlaObjectiveStatus resolution = slaService.evaluateResolutionStatus(oldResolution,
                incident.getCreatedAt(), incident.getResolutionDeadline(), incident.getResolvedAt(),
                incident.getClosedAt(), now, approachingThreshold);
        SlaStatus summary = slaService.compatibilityStatus(response, resolution);

        boolean changed = !Objects.equals(oldResponse, response)
                || !Objects.equals(oldResolution, resolution)
                || oldSummary != summary;
        if (changed) {
            incident.setResponseSlaStatus(response);
            incident.setResolutionSlaStatus(resolution);
            incident.setSlaStatus(summary);
            incidentRepository.saveAndFlush(incident);
            publishMeaningfulTransition(incident.getId(), SlaObjective.RESPONSE,
                    oldResponse, response, incident.getResponseDeadline(), now);
            publishMeaningfulTransition(incident.getId(), SlaObjective.RESOLUTION,
                    oldResolution, resolution, incident.getResolutionDeadline(), now);
        }
        return Optional.of(new Evaluation(incident.getId(), oldResponse, response,
                oldResolution, resolution, oldSummary, summary,
                incident.getResponseDeadline(), incident.getResolutionDeadline(), changed));
    }

    public double approachingThreshold() {
        return approachingThreshold;
    }

    public Instant approachCandidateCutoff(Instant now, Integer durationMinutes) {
        return slaService.approachCandidateCutoff(now, durationMinutes, approachingThreshold);
    }

    private void publishMeaningfulTransition(Integer incidentId, SlaObjective objective,
                                              SlaObjectiveStatus previous, SlaObjectiveStatus current,
                                              Instant deadline, Instant now) {
        if (current != SlaObjectiveStatus.APPROACHING_BREACH
                && current != SlaObjectiveStatus.BREACHED) return;
        SlaObjectiveStatus oldStatus = previous == null
                ? SlaObjectiveStatus.NOT_STARTED : previous;
        if (oldStatus == current) return;
        eventPublisher.publishEvent(new SlaObjectiveTransitionEvent(
                java.util.UUID.randomUUID(), incidentId, null, now, objective,
                oldStatus, current, deadline));
    }

    public record Evaluation(Integer incidentId,
                             SlaObjectiveStatus oldResponseStatus,
                             SlaObjectiveStatus responseStatus,
                             SlaObjectiveStatus oldResolutionStatus,
                             SlaObjectiveStatus resolutionStatus,
                             SlaStatus oldSummaryStatus,
                             SlaStatus summaryStatus,
                             Instant responseDeadline,
                             Instant resolutionDeadline,
                             boolean changed) { }
}
