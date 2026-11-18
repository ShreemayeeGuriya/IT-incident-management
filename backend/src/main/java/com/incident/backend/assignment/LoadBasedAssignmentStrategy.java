package com.incident.backend.assignment;

import com.incident.backend.model.Incident;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Picks the technician using the smallest share of their capacity, spreading work evenly. */
@Component
@Order(2)
public class LoadBasedAssignmentStrategy implements AssignmentStrategy {
    public static final String NAME = "LOAD_BASED";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Least loaded technician (open incidents / maximum workload); ties go to fewer incidents, then lower id";
    }

    @Override
    public Optional<TechnicianCandidate> choose(Incident incident, List<TechnicianCandidate> eligible) {
        return eligible.stream().min(LeastLoadedOrder.COMPARATOR);
    }
}
