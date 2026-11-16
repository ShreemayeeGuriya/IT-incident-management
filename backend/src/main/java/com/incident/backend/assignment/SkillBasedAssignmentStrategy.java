package com.incident.backend.assignment;

import com.incident.backend.model.Incident;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Picks a technician whose skills include the incident's category (e.g. a "Network" skill for a
 * Network incident); among those, the least loaded one. Skill names match category names,
 * ignoring case.
 */
@Component
@Order(1)
public class SkillBasedAssignmentStrategy implements AssignmentStrategy {
    public static final String NAME = "SKILL_BASED";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Technician with a skill matching the incident category; least loaded among them";
    }

    @Override
    public Optional<TechnicianCandidate> choose(Incident incident, List<TechnicianCandidate> eligible) {
        String required = requiredSkill(incident);
        return eligible.stream()
                .filter(candidate -> candidate.hasSkill(required))
                .min(LeastLoadedOrder.COMPARATOR);
    }

    @Override
    public String noMatchReason(Incident incident) {
        return "no available technician has the '" + requiredSkill(incident) + "' skill";
    }

    private static String requiredSkill(Incident incident) {
        return incident.getCategory() == null ? null : incident.getCategory().getName();
    }
}
