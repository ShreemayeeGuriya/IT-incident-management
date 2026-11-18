package com.incident.backend.priority;

import com.incident.backend.model.BusinessCriticality;
import com.incident.backend.model.Impact;
import com.incident.backend.model.Urgency;

/** Maps each LOW / MEDIUM / HIGH factor to a numeric weight of 1 / 2 / 3. */
final class FactorWeights {
    private FactorWeights() { }

    static int of(Impact impact) {
        return switch (impact) { case LOW -> 1; case MEDIUM -> 2; case HIGH -> 3; };
    }

    static int of(Urgency urgency) {
        return switch (urgency) { case LOW -> 1; case MEDIUM -> 2; case HIGH -> 3; };
    }

    static int of(BusinessCriticality criticality) {
        return switch (criticality) { case LOW -> 1; case MEDIUM -> 2; case HIGH -> 3; };
    }
}
