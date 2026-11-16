package com.incident.backend.assignment;

import com.incident.backend.exception.BadRequestException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * Reads and writes the assignment-related facts about technicians that have no JPA mapping:
 * skills ({@code skills}, {@code technician_skills}) and open-incident counts.
 * Uses the existing tables only; no schema changes.
 */
@Repository
public class TechnicianProfileRepository {
    /** Incidents that still occupy a technician. RESOLVED waits only for the reporter, so it is excluded. */
    static final List<String> ACTIVE_STATUSES = List.of("REPORTED", "CLASSIFIED", "PRIORITIZED", "ASSIGNED",
            "INVESTIGATING");

    private final JdbcTemplate jdbc;

    public TechnicianProfileRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** technician id -> number of open incidents assigned to them (technicians with none are absent). */
    public Map<Integer, Long> activeIncidentCounts() {
        Map<Integer, Long> counts = new HashMap<>();
        jdbc.query("SELECT assigned_technician_id, COUNT(*) FROM incidents "
                        + "WHERE assigned_technician_id IS NOT NULL AND status IN ("
                        + placeholders(ACTIVE_STATUSES.size()) + ") GROUP BY assigned_technician_id",
                rs -> { counts.put(rs.getInt(1), rs.getLong(2)); }, ACTIVE_STATUSES.toArray());
        return counts;
    }

    public long activeIncidentCount(Integer technicianId) {
        Object[] args = new Object[ACTIVE_STATUSES.size() + 1];
        args[0] = technicianId;
        for (int i = 0; i < ACTIVE_STATUSES.size(); i++) args[i + 1] = ACTIVE_STATUSES.get(i);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM incidents WHERE assigned_technician_id = ? "
                + "AND status IN (" + placeholders(ACTIVE_STATUSES.size()) + ")", Long.class, args);
        return count == null ? 0 : count;
    }

    /** technician id -> skill names. */
    public Map<Integer, Set<String>> skillsByTechnician() {
        Map<Integer, Set<String>> skills = new HashMap<>();
        jdbc.query("SELECT ts.technician_id, s.skill_name FROM technician_skills ts "
                        + "JOIN skills s ON s.skill_id = ts.skill_id",
                rs -> { skills.computeIfAbsent(rs.getInt(1), k -> new TreeSet<>()).add(rs.getString(2)); });
        return skills;
    }

    public Set<String> skillsOf(Integer technicianId) {
        return new TreeSet<>(jdbc.queryForList("SELECT s.skill_name FROM technician_skills ts "
                + "JOIN skills s ON s.skill_id = ts.skill_id WHERE ts.technician_id = ?", String.class, technicianId));
    }

    public List<String> allSkillNames() {
        return jdbc.queryForList("SELECT skill_name FROM skills ORDER BY skill_name", String.class);
    }

    /**
     * Replaces the technician's skills with the given ones.
     *
     * @throws BadRequestException if a skill name does not exist in the {@code skills} table
     */
    public void replaceSkills(Integer technicianId, Collection<String> skillNames) {
        Map<String, Integer> idsByLowerName = new HashMap<>();
        jdbc.query("SELECT skill_id, skill_name FROM skills",
                rs -> { idsByLowerName.put(TechnicianCandidate.normalise(rs.getString(2)), rs.getInt(1)); });
        Set<Integer> skillIds = new LinkedHashSet<>();
        for (String name : skillNames) {
            Integer id = name == null ? null : idsByLowerName.get(TechnicianCandidate.normalise(name));
            if (id == null) {
                throw new BadRequestException("Unknown skill '" + name + "'. Available: " + allSkillNames());
            }
            skillIds.add(id);
        }
        jdbc.update("DELETE FROM technician_skills WHERE technician_id = ?", technicianId);
        for (Integer skillId : skillIds) {
            jdbc.update("INSERT INTO technician_skills (technician_id, skill_id) VALUES (?, ?)", technicianId, skillId);
        }
    }

    private static String placeholders(int n) {
        return String.join(", ", Collections.nCopies(n, "?"));
    }
}
