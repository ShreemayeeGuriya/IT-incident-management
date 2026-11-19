package com.incident.backend.service;

import com.incident.backend.dto.IncidentInformationRequestResponse;
import com.incident.backend.events.AdditionalInformationRequestedEvent;
import com.incident.backend.events.AdditionalInformationRespondedEvent;
import com.incident.backend.exception.ConflictException;
import com.incident.backend.exception.BadRequestException;
import com.incident.backend.exception.ResourceNotFoundException;
import com.incident.backend.model.*;
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
public class IncidentInformationRequestService {
    private final IncidentAccess access;
    private final IncidentInformationRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public IncidentInformationRequestService(IncidentAccess access,
            IncidentInformationRequestRepository requestRepository, UserRepository userRepository,
            ApplicationEventPublisher eventPublisher) {
        this.access = access;
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    public IncidentInformationRequestResponse request(Long incidentId, String message, User actor) {
        String cleanMessage = requireMessage(message, "Request message is required");
        Incident incident = access.findForUpdate(incidentId);
        requireActive(incident);
        if (actor.getRole() != Role.TECHNICIAN || incident.getAssignedTechnician() == null
                || !incident.getAssignedTechnician().getUser().getId().equals(actor.getId())) {
            throw new AccessDeniedException("Only the assigned technician may request additional information");
        }
        Instant now = Instant.now();
        IncidentInformationRequest request = new IncidentInformationRequest();
        request.setIncident(incident);
        request.setRequestingTechnician(userRepository.getReferenceById(actor.getId()));
        request.setReporter(incident.getReporter());
        request.setRequestMessage(cleanMessage);
        IncidentInformationRequest saved = requestRepository.saveAndFlush(request);
        eventPublisher.publishEvent(new AdditionalInformationRequestedEvent(UUID.randomUUID(), incident.getId(),
                actor.getId(), now, saved.getId(), incident.getReporter().getId()));
        return IncidentInformationRequestResponse.from(saved);
    }

    public IncidentInformationRequestResponse respond(Long incidentId, Long requestId, String message, User actor) {
        String cleanMessage = requireMessage(message, "Response message is required");
        Incident incident = access.findForUpdate(incidentId);
        requireActive(incident);
        if (actor.getRole() != Role.EMPLOYEE || !incident.getReporter().getId().equals(actor.getId())) {
            throw new AccessDeniedException("Only the incident reporter may respond to information requests");
        }
        int id;
        try {
            id = Math.toIntExact(requestId);
        } catch (ArithmeticException ex) {
            throw new ResourceNotFoundException("Information request not found: " + requestId);
        }
        IncidentInformationRequest request = requestRepository.findByIdAndIncidentIdForUpdate(id, incident.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Information request not found: " + requestId));
        if (!request.getReporter().getId().equals(actor.getId())) {
            throw new AccessDeniedException("You may only respond to requests for your own incident");
        }
        if (request.getStatus() != InformationRequestStatus.OPEN) {
            throw new ConflictException("This information request has already been answered");
        }
        if (incident.getAssignedTechnician() == null
                || !incident.getAssignedTechnician().getUser().getId()
                        .equals(request.getRequestingTechnician().getId())) {
            throw new ConflictException("This information request is no longer active");
        }
        Instant now = Instant.now();
        request.respond(cleanMessage, now);
        IncidentInformationRequest saved = requestRepository.saveAndFlush(request);
        eventPublisher.publishEvent(new AdditionalInformationRespondedEvent(UUID.randomUUID(), incident.getId(),
                actor.getId(), now, saved.getId(), request.getRequestingTechnician().getId()));
        return IncidentInformationRequestResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<IncidentInformationRequestResponse> list(Long incidentId, User actor) {
        Incident incident = access.requireViewable(incidentId, actor);
        return requestRepository.findByIncidentIdOrderByRequestedAtAscIdAsc(incident.getId()).stream()
                .map(IncidentInformationRequestResponse::from).toList();
    }

    private void requireActive(Incident incident) {
        if (incident.getStatus() != IncidentStatus.ASSIGNED && incident.getStatus() != IncidentStatus.INVESTIGATING) {
            throw new ConflictException("Additional information requests require an active assigned incident");
        }
    }

    private String requireMessage(String message, String error) {
        if (message == null || message.isBlank()) throw new BadRequestException(error);
        String clean = message.trim();
        if (clean.length() > 3000) throw new BadRequestException("Message must be at most 3000 characters");
        return clean;
    }
}
