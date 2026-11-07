package com.incident.backend.service;

import com.incident.backend.exception.BadRequestException;
import com.incident.backend.exception.ConflictException;
import com.incident.backend.exception.ResourceNotFoundException;
import com.incident.backend.model.Priority;
import com.incident.backend.model.SlaPolicy;
import com.incident.backend.model.SlaObjectiveStatus;
import com.incident.backend.model.SlaStatus;
import com.incident.backend.repository.SlaPolicyRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class SlaService {
    private final SlaPolicyRepository slaPolicyRepository;

    public SlaService(SlaPolicyRepository slaPolicyRepository) {
        this.slaPolicyRepository = slaPolicyRepository;
    }

    public SlaPolicy resolvePolicy(Priority priority, Integer explicitPolicyId) {
        if (priority == null) {
            throw new BadRequestException("Incident priority is required to select an SLA policy");
        }

        SlaPolicy policy;
        if (explicitPolicyId != null) {
            policy = slaPolicyRepository.findById(explicitPolicyId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "SLA policy not found: " + explicitPolicyId));
            if (policy.getPriority() != priority) {
                throw new BadRequestException("SLA policy priority must match incident priority");
            }
            return policy;
        }

        return slaPolicyRepository.findByPriority(priority)
                .orElseThrow(() -> new ConflictException(
                        "No SLA policy is configured for priority " + priority));
    }

    public Deadlines calculateDeadlines(Instant originalCreatedAt, SlaPolicy policy) {
        if (originalCreatedAt == null) {
            throw new IllegalArgumentException("Incident createdAt is required to calculate SLA deadlines");
        }
        if (policy == null) {
            throw new IllegalArgumentException("SLA policy is required to calculate deadlines");
        }
        return new Deadlines(
                originalCreatedAt.plus(Duration.ofMinutes(policy.getResponseTimeMinutes())),
                originalCreatedAt.plus(Duration.ofMinutes(policy.getResolutionTimeMinutes())));
    }

    public SlaObjectiveStatus evaluateResponseStatus(SlaObjectiveStatus currentStatus,
                                                    Instant createdAt, Instant deadline,
                                                    Instant firstResponseAt, Instant resolvedAt,
                                                    Instant closedAt, Instant now,
                                                    double approachingThreshold) {
        Instant completedAt = firstResponseAt != null ? firstResponseAt
                : resolvedAt != null ? resolvedAt : closedAt;
        return evaluateObjective(currentStatus, createdAt, deadline, completedAt, now,
                approachingThreshold);
    }

    public SlaObjectiveStatus evaluateResolutionStatus(SlaObjectiveStatus currentStatus,
                                                      Instant createdAt, Instant deadline,
                                                      Instant resolvedAt, Instant closedAt,
                                                      Instant now, double approachingThreshold) {
        Instant completedAt = resolvedAt != null ? resolvedAt : closedAt;
        return evaluateObjective(currentStatus, createdAt, deadline, completedAt, now,
                approachingThreshold);
    }

    private SlaObjectiveStatus evaluateObjective(SlaObjectiveStatus currentStatus,
                                                Instant createdAt, Instant deadline,
                                                Instant completedAt, Instant now,
                                                double approachingThreshold) {
        if (now == null) throw new IllegalArgumentException("Current time is required for SLA evaluation");
        validateThreshold(approachingThreshold);

        SlaObjectiveStatus status = currentStatus == null
                ? SlaObjectiveStatus.NOT_STARTED : currentStatus;
        if (status == SlaObjectiveStatus.BREACHED || status == SlaObjectiveStatus.MET) return status;
        if (createdAt == null || deadline == null) return status;

        if (completedAt != null) {
            return completedAt.isAfter(deadline)
                    ? SlaObjectiveStatus.BREACHED : SlaObjectiveStatus.MET;
        }
        if (!now.isBefore(deadline)) return SlaObjectiveStatus.BREACHED;

        Duration allowed = Duration.between(createdAt, deadline);
        if (allowed.isZero() || allowed.isNegative()) return SlaObjectiveStatus.BREACHED;
        Duration elapsed = Duration.between(createdAt, now);
        long thresholdNanos = Math.round(allowed.toNanos() * approachingThreshold);
        if (!elapsed.isNegative() && elapsed.toNanos() >= thresholdNanos) {
            return SlaObjectiveStatus.APPROACHING_BREACH;
        }
        return SlaObjectiveStatus.WITHIN_SLA;
    }

    public SlaStatus compatibilityStatus(SlaObjectiveStatus responseStatus,
                                         SlaObjectiveStatus resolutionStatus) {
        SlaObjectiveStatus response = responseStatus == null
                ? SlaObjectiveStatus.NOT_STARTED : responseStatus;
        SlaObjectiveStatus resolution = resolutionStatus == null
                ? SlaObjectiveStatus.NOT_STARTED : resolutionStatus;

        boolean responseBreached = response == SlaObjectiveStatus.BREACHED;
        boolean resolutionBreached = resolution == SlaObjectiveStatus.BREACHED;
        if (responseBreached && resolutionBreached) return SlaStatus.BREACHED;
        if (responseBreached) return SlaStatus.RESPONSE_BREACHED;
        if (resolutionBreached) return SlaStatus.RESOLUTION_BREACHED;
        if (response == SlaObjectiveStatus.MET && resolution == SlaObjectiveStatus.MET) {
            return SlaStatus.MET;
        }
        if (response == SlaObjectiveStatus.NOT_STARTED
                && resolution == SlaObjectiveStatus.NOT_STARTED) return SlaStatus.NOT_STARTED;
        return SlaStatus.WITHIN_SLA;
    }

    public Instant approachCandidateCutoff(Instant now, Integer durationMinutes,
                                           double approachingThreshold) {
        if (now == null) throw new IllegalArgumentException("Current time is required for SLA candidate selection");
        validateThreshold(approachingThreshold);
        if (durationMinutes == null || durationMinutes <= 0) {
            throw new IllegalArgumentException("SLA duration must be greater than zero");
        }
        long remainingMillis = Math.round(durationMinutes * 60_000.0 * (1.0 - approachingThreshold));
        return now.plusMillis(remainingMillis);
    }

    private void validateThreshold(double approachingThreshold) {
        if (!Double.isFinite(approachingThreshold)
                || approachingThreshold <= 0.0 || approachingThreshold >= 1.0) {
            throw new IllegalArgumentException("Approaching-breach threshold must be between 0 and 1");
        }
    }

    public record Deadlines(Instant responseDeadline, Instant resolutionDeadline) { }
}
