package com.incident.backend.priority;

import com.incident.backend.model.BusinessCriticality;
import com.incident.backend.model.Priority;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Alternative strategy based on the ITIL impact/urgency matrix, with business criticality as a modifier.
 *
 * <pre>
 *                 Urgency HIGH   MEDIUM   LOW
 *   Impact HIGH           P1     P2       P3
 *          MEDIUM         P2     P3       P4
 *          LOW            P3     P4       P4
 * </pre>
 *
 * HIGH criticality raises the result one level (never above P1); LOW criticality lowers it one level
 * (never below P4). The score is the matrix level 1-4 after adjustment (1 = P1).
 */
@Component
@Order(2)
public class ImpactUrgencyMatrixPriorityStrategy implements PriorityStrategy {
    public static final String NAME = "IMPACT_URGENCY_MATRIX";

    /** MATRIX[impact][urgency] = level, where 1 is P1. Indexed by enum ordinal (LOW, MEDIUM, HIGH). */
    private static final int[][] MATRIX = {
            /* impact LOW    */ {4, 4, 3},
            /* impact MEDIUM */ {4, 3, 2},
            /* impact HIGH   */ {3, 2, 1},
    };

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "ITIL impact/urgency matrix; HIGH criticality raises one level, LOW lowers one level";
    }

    @Override
    public PriorityAssessment assess(PriorityInput input) {
        int base = MATRIX[input.impact().ordinal()][input.urgency().ordinal()];
        int adjusted = base;
        if (input.businessCriticality() == BusinessCriticality.HIGH) adjusted = Math.max(1, base - 1);
        if (input.businessCriticality() == BusinessCriticality.LOW) adjusted = Math.min(4, base + 1);
        Priority priority = Priority.values()[adjusted - 1];
        return new PriorityAssessment(priority, adjusted, NAME,
                "matrix(" + input.impact() + ", " + input.urgency() + ") = P" + base
                        + ", criticality " + input.businessCriticality() + " -> " + priority);
    }
}
