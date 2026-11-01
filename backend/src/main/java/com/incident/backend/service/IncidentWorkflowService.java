package com.incident.backend.service;

import com.incident.backend.dto.IncidentResponse;
import com.incident.backend.dto.StateChangeRequest;
import com.incident.backend.exception.BadRequestException;
import com.incident.backend.exception.ResourceNotFoundException;
import com.incident.backend.model.Incident;
import com.incident.backend.model.IncidentState;
import com.incident.backend.model.Resolution;
import com.incident.backend.model.User;
import com.incident.backend.repository.IncidentRepository;
import com.incident.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IncidentWorkflowService {

    private final IncidentRepository incidentRepository;
    private final UserRepository userRepository;
    private final IncidentAccess access;

    public IncidentWorkflowService(IncidentRepository incidentRepository,
                                   UserRepository userRepository, IncidentAccess access) {
        this.incidentRepository = incidentRepository;
        this.userRepository = userRepository;
        this.access = access;
    }

    // assigneeId == null means "unassign"
    public IncidentResponse assign(Long incidentId, Long assigneeId) {
        Incident incident = access.findOrThrow(incidentId);
        if (assigneeId == null) {
            incident.setAssignee(null);
        } else {
            User assignee = userRepository.findById(assigneeId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + assigneeId));
            if (!IncidentAccess.isStaff(assignee)) {
                throw new BadRequestException("Assignee must be a technician or admin");
            }
            incident.setAssignee(assignee);
        }
        return IncidentResponse.from(incidentRepository.saveAndFlush(incident));
    }

    public IncidentResponse changeState(Long incidentId, StateChangeRequest request) {
        Incident incident = access.findOrThrow(incidentId);
        boolean closing = request.state() == IncidentState.CLOSED;
        if (closing && (request.resolution() == null || request.resolution() == Resolution.UNRESOLVED)) {
            throw new BadRequestException("A resolution other than UNRESOLVED is required to close an incident");
        }
        incident.setState(request.state());
        if (request.resolution() != null) {
            incident.setResolution(request.resolution());
        }
        return IncidentResponse.from(incidentRepository.saveAndFlush(incident));
    }
}
