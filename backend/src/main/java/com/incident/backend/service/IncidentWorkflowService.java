package com.incident.backend.service;

import com.incident.backend.assignment.AssignmentRules;
import com.incident.backend.assignment.TechnicianCandidate;
import com.incident.backend.dto.IncidentResponse;
import com.incident.backend.dto.StateChangeRequest;
import com.incident.backend.events.IncidentStatusChangedEvent;
import com.incident.backend.events.TechnicianAssignmentChangedEvent;
import com.incident.backend.exception.ResourceNotFoundException;
import com.incident.backend.exception.InvalidStateTransitionException;
import com.incident.backend.lifecycle.IncidentLifecycle;
import com.incident.backend.model.Incident;
import com.incident.backend.model.IncidentStatus;
import com.incident.backend.model.Technician;
import com.incident.backend.model.User;
import com.incident.backend.repository.IncidentRepository;
import com.incident.backend.repository.TechnicianRepository;
import com.incident.backend.assignment.TechnicianProfileRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class IncidentWorkflowService {
    /** Statuses in which the technician may be removed without a replacement. */
    private static final Set<IncidentStatus> UNASSIGNABLE = EnumSet.of(
            IncidentStatus.REPORTED, IncidentStatus.CLASSIFIED, IncidentStatus.PRIORITIZED, IncidentStatus.ASSIGNED);

    private final IncidentRepository incidentRepository;
    private final TechnicianRepository technicianRepository;
    private final IncidentAccess access;
    private final ApplicationEventPublisher eventPublisher;
    private final IncidentLifecycle lifecycle;
    private final AssignmentRules assignmentRules;
    private final TechnicianProfileRepository technicianProfiles;
    private final EntityManager entityManager;

    public IncidentWorkflowService(IncidentRepository incidentRepository,
                                   TechnicianRepository technicianRepository, IncidentAccess access,
                                   ApplicationEventPublisher eventPublisher, IncidentLifecycle lifecycle,
                                   AssignmentRules assignmentRules, TechnicianProfileRepository technicianProfiles,
                                   EntityManager entityManager) {
        this.incidentRepository = incidentRepository;
        this.technicianRepository = technicianRepository;
        this.access = access;
        this.eventPublisher = eventPublisher;
        this.lifecycle = lifecycle;
        this.assignmentRules = assignmentRules;
        this.technicianProfiles = technicianProfiles;
        this.entityManager = entityManager;
    }

    // technicianId == null means "unassign".
    public IncidentResponse assign(Long incidentId, Long technicianId) {
        return assign(incidentId, technicianId, null);
    }

    /**
     * Assigns, reassigns or unassigns a technician.
     *
     * <p>Lifecycle effects: assigning a PRIORITIZED incident moves it to ASSIGNED; unassigning an
     * ASSIGNED incident moves it back to PRIORITIZED. Closed incidents cannot be reassigned, and an
     * incident that is already being worked on cannot be left without a technician.</p>
     */
    public IncidentResponse assign(Long incidentId, Long technicianId, User actor) {
        Incident incident = access.findForUpdate(incidentId);
        IncidentStatus status = incident.getStatus();
        Integer oldTechnicianId = technicianIdOf(incident);
        IncidentStatus nextStatus = null;

        if (technicianId == null) {
            if (oldTechnicianId != null && !UNASSIGNABLE.contains(status)) {
                throw InvalidStateTransitionException.cannotUnassign(status);
            }
            incident.setAssignedTechnician(null);
            if (status == IncidentStatus.ASSIGNED) nextStatus = IncidentStatus.PRIORITIZED;
        } else {
            if (lifecycle.currentState(incident).isTerminal()) {
                throw InvalidStateTransitionException.cannotAssign(status);
            }
            // Keep lock order consistent: incident first (above), then technician. The row lock
            // serializes competing assignments to different incidents for this technician.
            Technician technician = technicianRepository.findByIdForUpdate(Math.toIntExact(technicianId))
                    .orElseThrow(() -> new ResourceNotFoundException("Technician not found: " + technicianId));
            // The candidate may already be in the persistence context from auto-assignment's
            // strategy scan. Refresh after locking so eligibility uses current database values.
            entityManager.refresh(technician, LockModeType.PESSIMISTIC_WRITE);
            entityManager.refresh(technician.getUser());
            long activeIncidents = technicianProfiles.activeIncidentCount(technician.getId());
            // Re-assigning the same technician does not consume another workload slot.
            if (Objects.equals(oldTechnicianId, technician.getId())) activeIncidents--;
            assignmentRules.requireCanTake(new TechnicianCandidate(technician, activeIncidents, Set.of()));
            incident.setAssignedTechnician(technician);
            if (status == IncidentStatus.PRIORITIZED) nextStatus = IncidentStatus.ASSIGNED;
        }
        if (nextStatus != null) {
            lifecycle.transition(incident, nextStatus);
            applyStatusTimestamps(incident, status, nextStatus, Instant.now());
        }

        Incident saved = incidentRepository.saveAndFlush(incident);
        Integer newTechnicianId = technicianIdOf(saved);
        if (!Objects.equals(oldTechnicianId, newTechnicianId)) {
            publishAssignmentChanged(saved, actor, oldTechnicianId, newTechnicianId);
        }
        if (nextStatus != null) {
            publishStatusChanged(saved, actor, status, nextStatus,
                    "Incident status changed from " + status + " to " + nextStatus);
        }
        return IncidentResponse.from(saved);
    }

    public IncidentResponse changeState(Long incidentId, StateChangeRequest request) {
        return changeState(incidentId, request, null);
    }

    /**
     * Moves the incident to the requested status, enforcing the lifecycle rules.
     * Requesting the current status is a no-op.
     */
    public IncidentResponse changeState(Long incidentId, StateChangeRequest request, User actor) {
        Incident incident = access.findForUpdate(incidentId);
        IncidentStatus target = request.status();
        IncidentStatus previousStatus = incident.getStatus();
        if (previousStatus == target) {
            return IncidentResponse.from(incident);
        }

        return transitionAndSave(incident, target, actor,
                "Incident status changed from " + previousStatus + " to " + target);
    }

    /** Automatically advances a newly created incident through classification and prioritization. */
    public IncidentResponse advanceCreationToPrioritized(Long incidentId, User actor) {
        Incident incident = access.findForUpdate(incidentId);
        transitionAndSave(incident, IncidentStatus.CLASSIFIED, actor,
                "Automatically advanced incident from REPORTED to CLASSIFIED during creation");
        return transitionAndSave(incident, IncidentStatus.PRIORITIZED, actor,
                "Automatically advanced incident from CLASSIFIED to PRIORITIZED during creation");
    }

    private IncidentResponse transitionAndSave(Incident incident, IncidentStatus target, User actor,
                                               String auditDescription) {
        IncidentStatus previousStatus = incident.getStatus();

        lifecycle.transition(incident, target);

        // Going back from ASSIGNED to PRIORITIZED releases the technician.
        Integer oldTechnicianId = technicianIdOf(incident);
        boolean released = previousStatus == IncidentStatus.ASSIGNED && target == IncidentStatus.PRIORITIZED
                && oldTechnicianId != null;
        if (released) {
            incident.setAssignedTechnician(null);
        }
        applyStatusTimestamps(incident, previousStatus, target, Instant.now());

        Incident saved = incidentRepository.saveAndFlush(incident);
        if (released) {
            publishAssignmentChanged(saved, actor, oldTechnicianId, null);
        }
        publishStatusChanged(saved, actor, previousStatus, target, auditDescription);
        return IncidentResponse.from(saved);
    }

    private static void applyStatusTimestamps(Incident incident, IncidentStatus from, IncidentStatus to,
                                              Instant now) {
        if (to == IncidentStatus.INVESTIGATING && incident.getFirstResponseAt() == null) {
            incident.setFirstResponseAt(now);
        }
        if (from == IncidentStatus.RESOLVED && to == IncidentStatus.INVESTIGATING) {
            // Reopened: the earlier fix did not hold, so the incident is no longer resolved.
            incident.setResolvedAt(null);
        }
        if (to == IncidentStatus.RESOLVED && incident.getResolvedAt() == null) {
            incident.setResolvedAt(now);
        }
        if (to == IncidentStatus.CLOSED && incident.getClosedAt() == null) {
            incident.setClosedAt(now);
        }
    }

    private void publishAssignmentChanged(Incident incident, User actor, Integer oldTechnicianId,
                                          Integer newTechnicianId) {
        eventPublisher.publishEvent(new TechnicianAssignmentChangedEvent(UUID.randomUUID(), incident.getId(),
                actorId(actor), Instant.now(), oldTechnicianId, newTechnicianId,
                oldTechnicianId == null ? "Technician assigned" : newTechnicianId == null
                        ? "Technician unassigned" : "Technician assignment changed"));
    }

    private void publishStatusChanged(Incident incident, User actor, IncidentStatus from, IncidentStatus to,
                                      String description) {
        eventPublisher.publishEvent(new IncidentStatusChangedEvent(UUID.randomUUID(), incident.getId(),
                actorId(actor), Instant.now(), from.name(), to.name(), description));
    }

    private static Integer technicianIdOf(Incident incident) {
        return incident.getAssignedTechnician() == null ? null : incident.getAssignedTechnician().getId();
    }

    private static Integer actorId(User actor) {
        return actor == null ? null : actor.getId();
    }
}
