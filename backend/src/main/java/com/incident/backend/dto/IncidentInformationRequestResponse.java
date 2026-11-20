package com.incident.backend.dto;

import com.incident.backend.model.IncidentInformationRequest;
import com.incident.backend.model.InformationRequestStatus;
import java.time.Instant;

public record IncidentInformationRequestResponse(Long id, Long incidentId,
        Long requestingTechnicianId, Long reporterId, String requestMessage, Instant requestedAt,
        InformationRequestStatus status, String responseMessage, Instant respondedAt) {
    public static IncidentInformationRequestResponse from(IncidentInformationRequest request) {
        return new IncidentInformationRequestResponse(request.getId().longValue(),
                request.getIncident().getId().longValue(), request.getRequestingTechnician().getId().longValue(),
                request.getReporter().getId().longValue(), request.getRequestMessage(), request.getRequestedAt(),
                request.getStatus(), request.getResponseMessage(), request.getRespondedAt());
    }
}
