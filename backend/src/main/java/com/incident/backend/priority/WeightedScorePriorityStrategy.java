package com.incident.backend.priority;

import com.incident.backend.model.Priority;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Default strategy: {@code score = impact x urgency x criticality}, each weighted 1 (LOW) to 3 (HIGH),
 * so the score ranges from 1 to 27.
 *
 * <pre>
 *   score >= 18  -> P1  (all HIGH, or two HIGH and one MEDIUM)
 *   score >=  9  -> P2  (e.g. HIGH, HIGH, LOW  or  HIGH, MEDIUM, MEDIUM)
 *   score >=  4  -> P3  (e.g. all MEDIUM, or HIGH, MEDIUM, LOW)
 *   otherwise    -> P4
 * </pre>
 *
 * Multiplying (rather than adding) means one LOW factor pulls the priority down sharply, which
 * matches the brief's example formula.
 */
@Component
@Order(1)
public class WeightedScorePriorityStrategy implements PriorityStrategy {
    public static final String NAME = "WEIGHTED_SCORE";

    static final int P1_THRESHOLD = 18;
    static final int P2_THRESHOLD = 9;
    static final int P3_THRESHOLD = 4;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Impact x Urgency x Business criticality (each 1-3); >=18 P1, >=9 P2, >=4 P3, else P4";
    }

    @Override
    public PriorityAssessment assess(PriorityInput input) {
        int impact = FactorWeights.of(input.impact());
        int urgency = FactorWeights.of(input.urgency());
        int criticality = FactorWeights.of(input.businessCriticality());
        int score = impact * urgency * criticality;
        Priority priority = score >= P1_THRESHOLD ? Priority.P1
                : score >= P2_THRESHOLD ? Priority.P2
                : score >= P3_THRESHOLD ? Priority.P3
                : Priority.P4;
        return new PriorityAssessment(priority, score, NAME,
                impact + " x " + urgency + " x " + criticality + " = " + score + " -> " + priority);
    }
}
