package com.incident.backend.priority;

import com.incident.backend.model.BusinessCriticality;
import com.incident.backend.model.Impact;
import com.incident.backend.model.Incident;
import com.incident.backend.model.Urgency;

import java.util.Objects;

/** The factors a priority is calculated from. Immutable; all three factors are required. */
public record PriorityInput(Impact impact, Urgency urgency, BusinessCriticality businessCriticality) {

    public PriorityInput {
        Objects.requireNonNull(impact, "impact is required");
        Objects.requireNonNull(urgency, "urgency is required");
        Objects.requireNonNull(businessCriticality, "businessCriticality is required");
    }

    public static PriorityInput of(Incident incident) {
        return new PriorityInput(incident.getImpact(), incident.getUrgency(), incident.getBusinessCriticality());
    }
}
