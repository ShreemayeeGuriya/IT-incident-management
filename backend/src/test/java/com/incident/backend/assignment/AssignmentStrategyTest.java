package com.incident.backend.assignment;

import com.incident.backend.model.Incident;
import com.incident.backend.model.IncidentStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.incident.backend.assignment.Fixtures.candidate;
import static com.incident.backend.assignment.Fixtures.incident;
import static org.junit.jupiter.api.Assertions.*;

class AssignmentStrategyTest {
    private final Incident networkIncident = incident("Network", IncidentStatus.PRIORITIZED);

    @Test
    void loadBasedPicksTheLowestShareOfCapacity() {
        // 2/5 = 0.4 beats 1/2 = 0.5 even though it has more incidents
        var chosen = new LoadBasedAssignmentStrategy().choose(networkIncident, List.of(
                candidate(1, 1, 2), candidate(2, 2, 5), candidate(3, 4, 5)));
        assertEquals(2, chosen.orElseThrow().id());
    }

    @Test
    void loadBasedBreaksTiesByFewerIncidentsThenLowerId() {
        var strategy = new LoadBasedAssignmentStrategy();
        assertEquals(1, strategy.choose(networkIncident, List.of(candidate(2, 0, 5), candidate(1, 0, 3)))
                .orElseThrow().id());
        assertEquals(4, strategy.choose(networkIncident, List.of(candidate(3, 2, 4), candidate(4, 1, 2)))
                .orElseThrow().id());
    }

    @Test
    void skillBasedOnlyConsidersMatchingSkillIgnoringCase() {
        var chosen = new SkillBasedAssignmentStrategy().choose(networkIncident, List.of(
                candidate(1, 0, 5, "Hardware"),
                candidate(2, 3, 5, "network"),
                candidate(3, 1, 5, "NETWORK", "Software")));
        assertEquals(3, chosen.orElseThrow().id(), "least loaded of the two network technicians");
    }

    @Test
    void skillBasedReturnsEmptyAndExplainsWhenNobodyHasTheSkill() {
        var strategy = new SkillBasedAssignmentStrategy();
        assertTrue(strategy.choose(networkIncident, List.of(candidate(1, 0, 5, "Hardware"))).isEmpty());
        assertTrue(strategy.noMatchReason(networkIncident).contains("'Network'"));
    }

    @Test
    void roundRobinTakesTurnsAndWrapsAround() {
        var strategy = new RoundRobinAssignmentStrategy();
        var eligible = List.of(candidate(10, 0, 5), candidate(20, 0, 5), candidate(30, 0, 5));
        int[] expected = {10, 20, 30, 10};
        for (int id : expected) {
            var chosen = strategy.choose(networkIncident, eligible).orElseThrow();
            assertEquals(id, chosen.id());
            strategy.onAssigned(chosen);
        }
    }

    @Test
    void roundRobinPreviewDoesNotAdvance() {
        var strategy = new RoundRobinAssignmentStrategy();
        var eligible = List.of(candidate(1, 0, 5), candidate(2, 0, 5));
        assertEquals(1, strategy.choose(networkIncident, eligible).orElseThrow().id());
        assertEquals(1, strategy.choose(networkIncident, eligible).orElseThrow().id());
    }

    @Test
    void roundRobinSkipsTechniciansNoLongerEligible() {
        var strategy = new RoundRobinAssignmentStrategy();
        strategy.onAssigned(candidate(2, 0, 5));
        // technician 3 dropped out; next after 2 is 4
        assertEquals(4, strategy.choose(networkIncident, List.of(candidate(1, 0, 5), candidate(4, 0, 5)))
                .orElseThrow().id());
    }

    @Test
    void candidateCapacityAndLoad() {
        var c = candidate(1, 3, 4);
        assertTrue(c.hasCapacity());
        assertEquals(0.75, c.loadRatio());
        assertFalse(candidate(1, 4, 4).hasCapacity());
        assertEquals(1.0, candidate(1, 0, 0).loadRatio());
    }
}
