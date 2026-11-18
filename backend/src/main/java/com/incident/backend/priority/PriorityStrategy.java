package com.incident.backend.priority;

import com.incident.backend.strategy.NamedStrategy;

/**
 * An algorithm that turns an incident's impact, urgency and business criticality into a priority
 * (Strategy pattern). Implementations are interchangeable; {@link PriorityService} uses whichever
 * one is configured.
 */
public interface PriorityStrategy extends NamedStrategy {

    PriorityAssessment assess(PriorityInput input);
}
