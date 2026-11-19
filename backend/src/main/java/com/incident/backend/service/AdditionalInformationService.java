package com.incident.backend.service;

import com.incident.backend.dto.IncidentInformationRequestResponse;
import com.incident.backend.events.AdditionalInformationRequestedEvent;
import com.incident.backend.events.AdditionalInformationRespondedEvent;
import com.incident.backend.exception.BadRequestException;
import com.incident.backend.exception.ResourceNotFoundException;
import com.incident.backend.model.Incident;
import com.incident.backend.model.IncidentInformationRequest;
import com.incident.backend.model.IncidentStatus;
import com.incident.backend.model.InformationRequestStatus;
import com.incident.backend.model.Role;
import com.incident.backend.model.User;
import com.incident.backend.repository.IncidentInformationRequestRepository;
import com.incident.backend.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AdditionalInformationService {
    private final IncidentInformationRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final IncidentAccess access;
    private final ApplicationEventPublisher eventPublisher;

    public AdditionalInformationService(IncidentInformationRequestRepository requestRepository,
                                        UserRepository userRepository, IncidentAccess access,
                                        ApplicationEventPublisher eventPublisher) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.access = access;
        this.eventPublisher = eventPublisher;
    }

    public IncidentInformationRequestResponse request(Long incidentId, String message, User actor) {
        requireRole(actor, Role.TECHNICIAN);
        Incident incident = access.findForUpdate(incidentId);
        if (incident.getAssignedTechnician() == null
                || !incident.getAssignedTechnician().getUser().getId().equals(actor.getId())) {
            throw new AccessDeniedException("Only the assigned technician may request information");
        }
        if (incident.getStatus() != IncidentStatus.ASSIGNED && incident.getStatus() != IncidentStatus.INVESTIGATING) {
            throw new BadRequestException("Information can only be requested while an incident is assigned or being investigated");
        }

        IncidentInformationRequest request = new IncidentInformationRequest();
        request.setIncident(incident);
        request.setRequestingTechnician(userRepository.getReferenceById(actor.getId()));
        request.setReporter(incident.getReporter());
        request.setRequestMessage(message.trim());
        IncidentInformationRequest saved = requestRepository.saveAndFlush(request);
        eventPublisher.publishEvent(new AdditionalInformationRequestedEvent(UUID.randomUUID(), incident.getId(),
                actor.getId(), saved.getRequestedAt(), saved.getId(), incident.getReporter().getId()));
        return IncidentInformationRequestResponse.from(saved);
    }

    public IncidentInformationRequestResponse respond(Long incidentId, Long requestId, String message, User actor) {
        requireRole(actor, Role.EMPLOYEE);
        Incident incident = access.findForUpdate(incidentId);
        if (!incident.getReporter().getId().equals(actor.getId())) {
            throw new AccessDeniedException("Only the incident reporter may respond to information requests");
        }
        IncidentInformationRequest request = requestRepository.findByIdAndIncidentIdForUpdate(
                        Math.toIntExact(requestId), incident.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Information request not found: " + requestId));
        if (request.getStatus() != InformationRequestStatus.OPEN) {
            throw new BadRequestException("Information request has already been answered");
        }
        if (incident.getStatus() != IncidentStatus.ASSIGNED && incident.getStatus() != IncidentStatus.INVESTIGATING) {
            throw new BadRequestException("This incident is no longer awaiting information");
        }
        request.respond(message.trim(), Instant.now());
        IncidentInformationRequest saved = requestRepository.saveAndFlush(request);
        eventPublisher.publishEvent(new AdditionalInformationRespondedEvent(UUID.randomUUID(), incident.getId(),
                actor.getId(), saved.getRespondedAt(), saved.getId(), saved.getRequestingTechnician().getId()));
        return IncidentInformationRequestResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<IncidentInformationRequestResponse> list(Long incidentId, User actor) {
        Incident incident = access.requireViewable(incidentId, actor);
        return requestRepository.findByIncidentIdOrderByRequestedAtAscIdAsc(incident.getId()).stream()
                .map(IncidentInformationRequestResponse::from).toList();
    }

    private static void requireRole(User actor, Role role) {
        if (actor.getRole() != role) throw new AccessDeniedException("This action requires role " + role);
    }
}
