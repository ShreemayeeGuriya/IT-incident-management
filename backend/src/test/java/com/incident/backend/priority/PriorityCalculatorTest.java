package com.incident.backend.priority;

import com.incident.backend.model.BusinessCriticality;
import com.incident.backend.model.Impact;
import com.incident.backend.model.Priority;
import com.incident.backend.model.Urgency;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriorityCalculatorTest {
    private final PriorityCalculator calculator = new PriorityCalculator(
            List.of(new WeightedScorePriorityStrategy(), new ImpactUrgencyMatrixPriorityStrategy()),
            WeightedScorePriorityStrategy.NAME);

    @Test
    void strategySwitchChangesSubsequentAssessments() {
        PriorityInput input = new PriorityInput(Impact.LOW, Urgency.LOW, BusinessCriticality.HIGH);

        assertEquals(Priority.P4, calculator.assess(input).priority());

        calculator.strategies().select(ImpactUrgencyMatrixPriorityStrategy.NAME);

        assertEquals(Priority.P3, calculator.assess(input).priority());
        assertEquals(ImpactUrgencyMatrixPriorityStrategy.NAME, calculator.assess(input).strategy());
    }
}
