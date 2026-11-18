package com.incident.backend.assignment;

import com.incident.backend.model.Technician;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;

/**
 * A technician together with the facts strategies need to choose between technicians:
 * how many open incidents they hold and which skills they have. Immutable.
 */
public record TechnicianCandidate(Technician technician, long activeIncidents, Set<String> skills) {

    public TechnicianCandidate {
        skills = skills == null ? Collections.emptySet() : Set.copyOf(skills);
    }

    public Integer id() {
        return technician.getId();
    }

    public int maxWorkload() {
        return technician.getMaxWorkload();
    }

    public boolean hasCapacity() {
        return activeIncidents < maxWorkload();
    }

    /** Share of capacity in use, 0.0 (idle) to 1.0 (full). Lets technicians with different limits be compared. */
    public double loadRatio() {
        return maxWorkload() <= 0 ? 1.0 : (double) activeIncidents / maxWorkload();
    }

    public boolean hasSkill(String skill) {
        if (skill == null) return false;
        String wanted = normalise(skill);
        return skills.stream().anyMatch(s -> normalise(s).equals(wanted));
    }

    static String normalise(String skill) {
        return skill.trim().toLowerCase(Locale.ROOT);
    }
}
