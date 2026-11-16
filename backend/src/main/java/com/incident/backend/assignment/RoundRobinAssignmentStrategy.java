package com.incident.backend.assignment;

import com.incident.backend.model.Incident;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Takes turns: picks the next eligible technician after the one chosen last time, wrapping around.
 * The position is kept in memory, so it restarts from the first technician after a restart.
 */
@Component
@Order(3)
public class RoundRobinAssignmentStrategy implements AssignmentStrategy {
    public static final String NAME = "ROUND_ROBIN";

    private Integer lastChosenId;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Takes turns through eligible technicians in id order";
    }

    @Override
    public synchronized Optional<TechnicianCandidate> choose(Incident incident, List<TechnicianCandidate> eligible) {
        if (eligible.isEmpty()) return Optional.empty();
        return Optional.of(eligible.stream()
                .filter(c -> lastChosenId == null || c.id() > lastChosenId)
                .findFirst()
                .orElse(eligible.get(0)));
    }

    /** Only a real assignment moves the turn on, so previews do not skip anyone. */
    @Override
    public synchronized void onAssigned(TechnicianCandidate chosen) {
        lastChosenId = chosen.id();
    }

    /** Forgets the last position (used by tests). */
    synchronized void reset() {
        lastChosenId = null;
    }
}
