package com.incident.backend.priority;

import com.incident.backend.dto.IncidentResponse;
import com.incident.backend.dto.IncidentUpdateRequest;
import com.incident.backend.exception.IncidentNotEligibleException;
import com.incident.backend.model.Incident;
import com.incident.backend.model.IncidentStatus;
import com.incident.backend.model.User;
import com.incident.backend.service.IncidentAccess;
import com.incident.backend.service.IncidentService;
import com.incident.backend.strategy.StrategyRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Calculates incident priority with the active {@link PriorityStrategy}.
 *
 * <p>Applying a new priority goes through {@link IncidentService#update}, so the existing SLA
 * policy selection, deadline recalculation and audit logging all run unchanged.</p>
 */
@Service
public class PriorityService {
    /** Once an incident is resolved its priority is part of the record and is no longer recalculated. */
    static final Set<IncidentStatus> LOCKED = EnumSet.of(
            IncidentStatus.RESOLVED, IncidentStatus.VERIFIED, IncidentStatus.CLOSED);

    private final StrategyRegistry<PriorityStrategy> registry;
    private final IncidentAccess access;
    private final IncidentService incidentService;

    public PriorityService(List<PriorityStrategy> strategies,
                           @Value("${app.priority.strategy:" + WeightedScorePriorityStrategy.NAME + "}")
                           String defaultStrategy,
                           IncidentAccess access, IncidentService incidentService) {
        this.registry = new StrategyRegistry<>("priority", strategies, defaultStrategy);
        this.access = access;
        this.incidentService = incidentService;
    }

    public PriorityAssessment assess(PriorityInput input) {
        return registry.active().assess(input);
    }

    public StrategyRegistry<PriorityStrategy> strategies() {
        return registry;
    }

    /** Calculates what the incident's priority should be, without changing it. */
    @Transactional(readOnly = true)
    public PriorityAssessment assess(Long incidentId, User current) {
        Incident incident = access.requireViewable(incidentId, current);
        return assess(PriorityInput.of(incident));
    }

    /**
     * Recalculates the incident's priority and saves it if it changed.
     *
     * @throws IncidentNotEligibleException if the incident is already resolved, verified or closed
     */
    @Transactional
    public Recalculation recalculate(Long incidentId, User actor) {
        Incident incident = access.requireViewableForUpdate(incidentId, actor);
        if (LOCKED.contains(incident.getStatus())) {
            throw new IncidentNotEligibleException("recalculate priority", incident.getStatus(),
                    "priority is fixed once an incident is resolved");
        }
        PriorityAssessment assessment = assess(PriorityInput.of(incident));
        boolean changed = assessment.priority() != incident.getPriority();
        IncidentResponse result = changed
                ? incidentService.update(incidentId, priorityOnly(assessment), actor)
                : IncidentResponse.from(incident);
        return new Recalculation(assessment, changed, result);
    }

    private static IncidentUpdateRequest priorityOnly(PriorityAssessment assessment) {
        return new IncidentUpdateRequest(null, null, null, null, null, null, null,
                assessment.priority(), null, null, null);
    }

    /** Outcome of {@link #recalculate}. */
    public record Recalculation(PriorityAssessment assessment, boolean changed, IncidentResponse incident) { }
}
