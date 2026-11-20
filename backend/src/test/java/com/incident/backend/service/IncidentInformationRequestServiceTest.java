package com.incident.backend.service;

import com.incident.backend.exception.BadRequestException;
import com.incident.backend.exception.ConflictException;
import com.incident.backend.model.*;
import com.incident.backend.repository.IncidentInformationRequestRepository;
import com.incident.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentInformationRequestServiceTest {
    @Mock IncidentAccess access;
    @Mock IncidentInformationRequestRepository requestRepository;
    @Mock UserRepository userRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock Incident incident;
    @Mock User actor;

    IncidentInformationRequestService service;

    @BeforeEach
    void setUp() {
        service = new IncidentInformationRequestService(access, requestRepository, userRepository, eventPublisher);
    }

    @Test
    void rejectsBlankRequestAndResponseMessagesAtServiceBoundary() {
        assertThrows(BadRequestException.class, () -> service.request(1L, "  ", actor));
        assertThrows(BadRequestException.class, () -> service.respond(1L, 2L, "\n", actor));
        verifyNoInteractions(access, requestRepository, userRepository, eventPublisher);
    }

    @Test
    void rejectsRequestFromTechnicianWhoIsNotAssigned() {
        Technician assigned = mock(Technician.class);
        User assignedUser = mock(User.class);
        when(actor.getRole()).thenReturn(Role.TECHNICIAN);
        when(actor.getId()).thenReturn(10);
        when(assigned.getUser()).thenReturn(assignedUser);
        when(assignedUser.getId()).thenReturn(11);
        when(incident.getStatus()).thenReturn(IncidentStatus.ASSIGNED);
        when(incident.getAssignedTechnician()).thenReturn(assigned);
        when(access.findForUpdate(1L)).thenReturn(incident);

        assertThrows(AccessDeniedException.class, () -> service.request(1L, "Please attach a screenshot", actor));
        verifyNoInteractions(requestRepository, eventPublisher);
    }

    @Test
    void rejectsASecondResponseToAnAnsweredRequest() {
        IncidentInformationRequest request = mock(IncidentInformationRequest.class);
        when(actor.getRole()).thenReturn(Role.EMPLOYEE);
        when(actor.getId()).thenReturn(10);
        when(incident.getId()).thenReturn(1);
        when(incident.getStatus()).thenReturn(IncidentStatus.ASSIGNED);
        when(incident.getReporter()).thenReturn(actor);
        when(access.findForUpdate(1L)).thenReturn(incident);
        when(requestRepository.findByIdAndIncidentIdForUpdate(2, 1)).thenReturn(Optional.of(request));
        when(request.getReporter()).thenReturn(actor);
        when(request.getStatus()).thenReturn(InformationRequestStatus.RESPONDED);

        assertThrows(ConflictException.class, () -> service.respond(1L, 2L, "Additional details", actor));
        verify(eventPublisher, never()).publishEvent(any());
    }
}
