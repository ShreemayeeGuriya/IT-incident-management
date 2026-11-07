package com.incident.backend.service;

import com.incident.backend.model.SlaObjectiveStatus;
import com.incident.backend.repository.IncidentRepository;
import com.incident.backend.repository.SlaPolicyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;

/** Bounded, periodic candidate selection; each incident is evaluated in its own transaction. */
@Component
public class SlaMonitorScheduler {
    private static final Logger log = LoggerFactory.getLogger(SlaMonitorScheduler.class);

    private final SlaPolicyRepository policyRepository;
    private final IncidentRepository incidentRepository;
    private final SlaMonitoringService monitoringService;
    private final int batchSize;

    public SlaMonitorScheduler(SlaPolicyRepository policyRepository,
                               IncidentRepository incidentRepository,
                               SlaMonitoringService monitoringService,
                               @Value("${app.sla.monitor-batch-size:100}") int batchSize) {
        if (batchSize < 1) throw new IllegalArgumentException("app.sla.monitor-batch-size must be positive");
        this.policyRepository = policyRepository;
        this.incidentRepository = incidentRepository;
        this.monitoringService = monitoringService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.sla.monitor-interval-ms:60000}",
            initialDelayString = "${app.sla.monitor-initial-delay-ms:60000}")
    public void scheduledRun() {
        runMonitoringCycle(Instant.now());
    }

    /** Exposed for deterministic integration tests and operational invocation. */
    public void runMonitoringCycle(Instant now) {
        policyRepository.findAll().forEach(policy -> {
            Instant responseCutoff = monitoringService.approachCandidateCutoff(
                    now, policy.getResponseTimeMinutes());
            Instant resolutionCutoff = monitoringService.approachCandidateCutoff(
                    now, policy.getResolutionTimeMinutes());
            try {
                incidentRepository.findSlaCandidateIds(policy.getId(), SlaObjectiveStatus.NOT_STARTED,
                                SlaObjectiveStatus.WITHIN_SLA, SlaObjectiveStatus.APPROACHING_BREACH,
                                responseCutoff, resolutionCutoff, now, PageRequest.of(0, batchSize))
                        .forEach(id -> {
                            try {
                                monitoringService.evaluateIncident(id, now);
                            } catch (RuntimeException exception) {
                                // A failed audit/notification rolls back this incident only; a later cycle retries it.
                                log.error("SLA evaluation failed for incident {}", id, exception);
                            }
                        });
            } catch (RuntimeException exception) {
                log.error("SLA candidate selection failed for policy {}", policy.getId(), exception);
            }
        });
    }
}
