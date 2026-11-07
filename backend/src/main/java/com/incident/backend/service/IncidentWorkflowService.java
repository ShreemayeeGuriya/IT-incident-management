package com.incident.backend.service;

import com.incident.backend.dto.IncidentResponse;
import com.incident.backend.dto.StateChangeRequest;
import com.incident.backend.events.IncidentStatusChangedEvent;
import com.incident.backend.events.TechnicianAssignmentChangedEvent;
import com.incident.backend.exception.ResourceNotFoundException;
import com.incident.backend.exception.BadRequestException;
import com.incident.backend.model.Incident;
import com.incident.backend.model.IncidentStatus;
import com.incident.backend.model.Technician;
import com.incident.backend.repository.IncidentRepository;
import com.incident.backend.repository.TechnicianRepository;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class IncidentWorkflowService {
    private final IncidentRepository incidentRepository;
    private final TechnicianRepository technicianRepository;
    private final IncidentAccess access;
    private final ApplicationEventPublisher eventPublisher;

    public IncidentWorkflowService(IncidentRepository incidentRepository,
                                   TechnicianRepository technicianRepository, IncidentAccess access,
                                   ApplicationEventPublisher eventPublisher) {
        this.incidentRepository = incidentRepository;
        this.technicianRepository = technicianRepository;
        this.access = access;
        this.eventPublisher = eventPublisher;
    }

    // technicianId == null means "unassign".
    public IncidentResponse assign(Long incidentId, Long technicianId) {
        return assign(incidentId, technicianId, null);
    }

    public IncidentResponse assign(Long incidentId, Long technicianId, com.incident.backend.model.User actor) {
        Incident incident = access.findForUpdate(incidentId);
        Integer oldTechnicianId = incident.getAssignedTechnician() == null ? null : incident.getAssignedTechnician().getId();
        if (technicianId == null) {
            incident.setAssignedTechnician(null);
        } else {
            Technician technician = technicianRepository.findById(Math.toIntExact(technicianId))
                    .orElseThrow(() -> new ResourceNotFoundException("Technician not found: " + technicianId));
            if (technician.getUser().getRole() != com.incident.backend.model.Role.TECHNICIAN) {
                throw new BadRequestException("The selected technician profile is not active");
            }
            incident.setAssignedTechnician(technician);
        }
        Incident saved = incidentRepository.saveAndFlush(incident);
        Integer newTechnicianId = saved.getAssignedTechnician() == null ? null : saved.getAssignedTechnician().getId();
        if (!java.util.Objects.equals(oldTechnicianId, newTechnicianId)) {
            eventPublisher.publishEvent(new TechnicianAssignmentChangedEvent(UUID.randomUUID(), saved.getId(),
                    actor == null ? null : actor.getId(), Instant.now(), oldTechnicianId, newTechnicianId,
                    oldTechnicianId == null ? "Technician assigned" : newTechnicianId == null
                            ? "Technician unassigned" : "Technician assignment changed"));
        }
        return IncidentResponse.from(saved);
    }

    public IncidentResponse changeState(Long incidentId, StateChangeRequest request) {
        return changeState(incidentId, request, null);
    }

    public IncidentResponse changeState(Long incidentId, StateChangeRequest request,
                                        com.incident.backend.model.User actor) {
        Incident incident = access.findForUpdate(incidentId);
        IncidentStatus status = request.status();
        IncidentStatus previousStatus = incident.getStatus();
        Instant now = Instant.now();
        incident.setStatus(status);
        if (status == IncidentStatus.INVESTIGATING && incident.getFirstResponseAt() == null) {
            incident.setFirstResponseAt(now);
        }
        if (status == IncidentStatus.RESOLVED && incident.getResolvedAt() == null) {
            incident.setResolvedAt(now);
        }
        if (status == IncidentStatus.CLOSED && incident.getClosedAt() == null) {
            incident.setClosedAt(now);
        }
        Incident saved = incidentRepository.saveAndFlush(incident);
        if (previousStatus != status) {
            eventPublisher.publishEvent(new IncidentStatusChangedEvent(UUID.randomUUID(), saved.getId(),
                    actor == null ? null : actor.getId(), Instant.now(), previousStatus.name(), status.name()));
        }
        return IncidentResponse.from(saved);
    }
}
