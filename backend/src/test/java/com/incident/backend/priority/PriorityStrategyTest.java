package com.incident.backend.priority;

import com.incident.backend.model.BusinessCriticality;
import com.incident.backend.model.Impact;
import com.incident.backend.model.Priority;
import com.incident.backend.model.Urgency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PriorityStrategyTest {
    private final WeightedScorePriorityStrategy weighted = new WeightedScorePriorityStrategy();
    private final ImpactUrgencyMatrixPriorityStrategy matrix = new ImpactUrgencyMatrixPriorityStrategy();

    private static PriorityInput in(String i, String u, String c) {
        return new PriorityInput(Impact.valueOf(i), Urgency.valueOf(u), BusinessCriticality.valueOf(c));
    }

    private static List<PriorityInput> allInputs() {
        List<PriorityInput> inputs = new ArrayList<>();
        for (Impact i : Impact.values())
            for (Urgency u : Urgency.values())
                for (BusinessCriticality c : BusinessCriticality.values())
                    inputs.add(new PriorityInput(i, u, c));
        return inputs;
    }

    @ParameterizedTest(name = "{0},{1},{2} -> score {3}, {4}")
    @CsvSource({
            "HIGH,HIGH,HIGH,27,P1",
            "HIGH,HIGH,MEDIUM,18,P1",
            "HIGH,MEDIUM,HIGH,18,P1",
            "HIGH,HIGH,LOW,9,P2",
            "HIGH,MEDIUM,MEDIUM,12,P2",
            "MEDIUM,MEDIUM,MEDIUM,8,P3",
            "HIGH,MEDIUM,LOW,6,P3",
            "MEDIUM,MEDIUM,LOW,4,P3",
            "HIGH,LOW,LOW,3,P4",
            "MEDIUM,LOW,LOW,2,P4",
            "LOW,LOW,LOW,1,P4"})
    void weightedScoreExamples(String i, String u, String c, int score, Priority expected) {
        PriorityAssessment a = weighted.assess(in(i, u, c));
        assertEquals(score, a.score());
        assertEquals(expected, a.priority());
        assertEquals(WeightedScorePriorityStrategy.NAME, a.strategy());
        assertTrue(a.explanation().endsWith("= " + score + " -> " + expected), a.explanation());
    }

    @ParameterizedTest(name = "{0},{1},{2} -> {3}")
    @CsvSource({
            "HIGH,HIGH,MEDIUM,P1",
            "HIGH,HIGH,HIGH,P1",
            "HIGH,HIGH,LOW,P2",
            "HIGH,MEDIUM,MEDIUM,P2",
            "HIGH,MEDIUM,HIGH,P1",
            "MEDIUM,MEDIUM,MEDIUM,P3",
            "MEDIUM,MEDIUM,HIGH,P2",
            "LOW,HIGH,MEDIUM,P3",
            "LOW,LOW,MEDIUM,P4",
            "LOW,LOW,HIGH,P3",
            "LOW,LOW,LOW,P4"})
    void matrixExamples(String i, String u, String c, Priority expected) {
        assertEquals(expected, matrix.assess(in(i, u, c)).priority());
    }

    /** Raising any single factor must never lower the priority. */
    @Test
    void bothStrategiesAreMonotonic() {
        for (PriorityStrategy strategy : List.of(weighted, matrix)) {
            for (PriorityInput input : allInputs()) {
                Priority base = strategy.assess(input).priority();
                for (PriorityInput higher : raisedByOne(input)) {
                    Priority raised = strategy.assess(higher).priority();
                    assertTrue(raised.ordinal() <= base.ordinal(),
                            strategy.name() + ": " + input + " gave " + base + " but " + higher + " gave " + raised);
                }
            }
        }
    }

    @Test
    void extremesMapToP1AndP4AndEveryPriorityIsReachable() {
        for (PriorityStrategy strategy : List.of(weighted, matrix)) {
            assertEquals(Priority.P1, strategy.assess(in("HIGH", "HIGH", "HIGH")).priority(), strategy.name());
            assertEquals(Priority.P4, strategy.assess(in("LOW", "LOW", "LOW")).priority(), strategy.name());
            Map<Priority, Integer> counts = new EnumMap<>(Priority.class);
            allInputs().forEach(x -> counts.merge(strategy.assess(x).priority(), 1, Integer::sum));
            assertEquals(4, counts.size(), strategy.name() + " should use every priority: " + counts);
        }
    }

    @Test
    void inputRejectsMissingFactors() {
        assertThrows(NullPointerException.class, () -> new PriorityInput(null, Urgency.LOW, BusinessCriticality.LOW));
        assertThrows(NullPointerException.class, () -> new PriorityInput(Impact.LOW, null, BusinessCriticality.LOW));
        assertThrows(NullPointerException.class, () -> new PriorityInput(Impact.LOW, Urgency.LOW, null));
    }

    private static List<PriorityInput> raisedByOne(PriorityInput p) {
        List<PriorityInput> out = new ArrayList<>();
        if (p.impact().ordinal() < 2)
            out.add(new PriorityInput(Impact.values()[p.impact().ordinal() + 1], p.urgency(), p.businessCriticality()));
        if (p.urgency().ordinal() < 2)
            out.add(new PriorityInput(p.impact(), Urgency.values()[p.urgency().ordinal() + 1], p.businessCriticality()));
        if (p.businessCriticality().ordinal() < 2)
            out.add(new PriorityInput(p.impact(), p.urgency(),
                    BusinessCriticality.values()[p.businessCriticality().ordinal() + 1]));
        return out;
    }
}
