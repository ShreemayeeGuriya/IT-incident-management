package com.incident.backend.dto;

import com.incident.backend.model.Incident;
import com.incident.backend.model.*;
import java.time.Instant;

public record IncidentResponse(
        Long id,
        UserResponse reporter,
        TechnicianResponse assignedTechnician,
        CategoryResponse category,
        ServiceResponse service,
        SlaPolicyResponse slaPolicy,
        String description,
        Impact impact,
        Urgency urgency,
        BusinessCriticality businessCriticality,
        Priority priority,
        IncidentStatus status,
        SlaStatus slaStatus,
        SlaObjectiveStatus responseSlaStatus,
        SlaObjectiveStatus resolutionSlaStatus,
        String investigationDetails,
        String resolutionDetails,
        String closureDetails,
        Instant createdAt,
        Instant firstResponseAt,
        Instant resolvedAt,
        Instant closedAt,
        Instant responseDeadline,
        Instant resolutionDeadline) {

    public static IncidentResponse from(Incident i) {
        return new IncidentResponse(
                i.getId().longValue(), UserResponse.from(i.getReporter()),
                i.getAssignedTechnician() == null ? null : TechnicianResponse.from(i.getAssignedTechnician()),
                CategoryResponse.from(i.getCategory()), ServiceResponse.from(i.getService()),
                i.getSlaPolicy() == null ? null : SlaPolicyResponse.from(i.getSlaPolicy()),
                i.getDescription(), i.getImpact(), i.getUrgency(), i.getBusinessCriticality(),
                i.getPriority(), i.getStatus(), i.getSlaStatus(), i.getResponseSlaStatus(),
                i.getResolutionSlaStatus(), i.getInvestigationDetails(),
                i.getResolutionDetails(), i.getClosureDetails(), i.getCreatedAt(),
                i.getFirstResponseAt(), i.getResolvedAt(), i.getClosedAt(),
                i.getResponseDeadline(), i.getResolutionDeadline());
    }
}
