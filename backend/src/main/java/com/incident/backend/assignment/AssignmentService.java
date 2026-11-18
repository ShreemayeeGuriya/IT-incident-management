package com.incident.backend.assignment;

import com.incident.backend.dto.IncidentResponse;
import com.incident.backend.exception.NoTechnicianAvailableException;
import com.incident.backend.exception.ResourceNotFoundException;
import com.incident.backend.model.Incident;
import com.incident.backend.model.Role;
import com.incident.backend.model.Technician;
import com.incident.backend.model.User;
import com.incident.backend.repository.TechnicianRepository;
import com.incident.backend.service.IncidentAccess;
import com.incident.backend.service.IncidentWorkflowService;
import com.incident.backend.strategy.StrategyRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recommends or assigns a technician using the active {@link AssignmentStrategy}, after filtering
 * candidates through {@link AssignmentRules}.
 *
 * <p>The actual assignment goes through {@link IncidentWorkflowService#assign}, so the lifecycle
 * change (PRIORITIZED to ASSIGNED), audit entry and notifications all happen as for a manual
 * assignment.</p>
 */
@Service
public class AssignmentService {
    private final StrategyRegistry<AssignmentStrategy> registry;
    private final AssignmentRules rules;
    private final TechnicianRepository technicianRepository;
    private final TechnicianProfileRepository profiles;
    private final IncidentAccess access;
    private final IncidentWorkflowService workflowService;

    public AssignmentService(List<AssignmentStrategy> strategies,
                             @Value("${app.assignment.strategy:" + SkillBasedAssignmentStrategy.NAME + "}")
                             String defaultStrategy,
                             AssignmentRules rules, TechnicianRepository technicianRepository,
                             TechnicianProfileRepository profiles, IncidentAccess access,
                             IncidentWorkflowService workflowService) {
        this.registry = new StrategyRegistry<>("assignment", strategies, defaultStrategy);
        this.rules = rules;
        this.technicianRepository = technicianRepository;
        this.profiles = profiles;
        this.access = access;
        this.workflowService = workflowService;
    }

    public StrategyRegistry<AssignmentStrategy> strategies() {
        return registry;
    }

    /** Who the active strategy would choose, without assigning. */
    @Transactional(readOnly = true)
    public Recommendation recommend(Long incidentId) {
        Incident incident = access.findOrThrow(incidentId);
        rules.requireAutoAssignable(incident);
        AssignmentStrategy strategy = registry.active();
        return new Recommendation(strategy.name(), choose(strategy, incident));
    }

    /**
     * Chooses a technician with the active strategy and assigns the incident to them.
     *
     * @throws com.incident.backend.exception.IncidentNotEligibleException if the incident is not PRIORITIZED
     *                                                                     or already has a technician
     * @throws NoTechnicianAvailableException                              if nobody suitable is free
     */
    @Transactional
    public Result autoAssign(Long incidentId, User actor) {
        Incident incident = access.findForUpdate(incidentId);
        rules.requireAutoAssignable(incident);
        AssignmentStrategy strategy = registry.active();
        TechnicianCandidate chosen = choose(strategy, incident);
        IncidentResponse assigned = workflowService.assign(incidentId, chosen.id().longValue(), actor);
        strategy.onAssigned(chosen);
        return new Result(strategy.name(), TechnicianSummary.of(chosen), assigned);
    }

    /**
     * Checks that a specific technician may take another incident. Intended for manual assignment too.
     *
     * @throws com.incident.backend.exception.TechnicianUnavailableException      if not available
     * @throws com.incident.backend.exception.TechnicianWorkloadExceededException if at maximum workload
     */
    @Transactional(readOnly = true)
    public void requireCanTake(Integer technicianId) {
        Technician technician = technicianRepository.findById(technicianId)
                .orElseThrow(() -> new ResourceNotFoundException("Technician not found: " + technicianId));
        rules.requireCanTake(new TechnicianCandidate(technician, profiles.activeIncidentCount(technicianId),
                profiles.skillsOf(technicianId)));
    }

    /** Every technician with open-incident count, capacity and skills, for the workload report. */
    @Transactional(readOnly = true)
    public List<TechnicianSummary> workload() {
        return candidates().stream().map(TechnicianSummary::of).toList();
    }

    @Transactional(readOnly = true)
    public Set<String> skillsOf(Integer technicianId) {
        requireTechnician(technicianId);
        return profiles.skillsOf(technicianId);
    }

    @Transactional
    public Set<String> replaceSkills(Integer technicianId, Collection<String> skills) {
        requireTechnician(technicianId);
        profiles.replaceSkills(technicianId, skills);
        return profiles.skillsOf(technicianId);
    }

    public List<String> allSkills() {
        return profiles.allSkillNames();
    }

    private TechnicianCandidate choose(AssignmentStrategy strategy, Incident incident) {
        List<TechnicianCandidate> eligible = candidates().stream().filter(rules::canTake).toList();
        if (eligible.isEmpty()) {
            throw new NoTechnicianAvailableException(strategy.name(),
                    "every technician is unavailable or at maximum workload");
        }
        return strategy.choose(incident, eligible)
                .orElseThrow(() -> new NoTechnicianAvailableException(strategy.name(),
                        strategy.noMatchReason(incident)));
    }

    /** All current technicians, ordered by id. */
    private List<TechnicianCandidate> candidates() {
        Map<Integer, Long> counts = profiles.activeIncidentCounts();
        Map<Integer, Set<String>> skills = profiles.skillsByTechnician();
        return technicianRepository.findAllByUserRoleOrderByIdAsc(Role.TECHNICIAN).stream()
                .map(t -> new TechnicianCandidate(t, counts.getOrDefault(t.getId(), 0L),
                        skills.getOrDefault(t.getId(), Set.of())))
                .toList();
    }

    private void requireTechnician(Integer technicianId) {
        if (!technicianRepository.existsById(technicianId)) {
            throw new ResourceNotFoundException("Technician not found: " + technicianId);
        }
    }

    /** A technician as shown in API responses. */
    public record TechnicianSummary(Integer technicianId, String name, String availability,
                                    long activeIncidents, int maxWorkload, Set<String> skills) {
        static TechnicianSummary of(TechnicianCandidate c) {
            return new TechnicianSummary(c.id(), c.technician().getUser().getName(),
                    c.technician().getAvailabilityStatus().name(), c.activeIncidents(), c.maxWorkload(),
                    c.skills());
        }
    }

    public record Recommendation(String strategy, TechnicianSummary technician) {
        Recommendation(String strategy, TechnicianCandidate candidate) {
            this(strategy, TechnicianSummary.of(candidate));
        }
    }

    public record Result(String strategy, TechnicianSummary technician, IncidentResponse incident) { }
}
