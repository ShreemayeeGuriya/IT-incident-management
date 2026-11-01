package com.incident.backend.dto;

import com.incident.backend.model.Classification;
import com.incident.backend.model.Incident;
import com.incident.backend.model.IncidentState;
import com.incident.backend.model.Priority;
import com.incident.backend.model.Reproducibility;
import com.incident.backend.model.Resolution;

import java.time.Instant;

public record IncidentResponse(
        Long id,
        String title,
        String description,
        String component,
        Classification classification,
        Reproducibility reproducibility,
        Priority priority,
        IncidentState state,
        Resolution resolution,
        UserResponse requestor,
        UserResponse assignee,
        String milestone,
        String category,
        String keywords,
        String rootCause,
        Instant createdAt,
        Instant updatedAt) {

    public static IncidentResponse from(Incident i) {
        return new IncidentResponse(
                i.getId(), i.getTitle(), i.getDescription(), i.getComponent(),
                i.getClassification(), i.getReproducibility(), i.getPriority(),
                i.getState(), i.getResolution(),
                UserResponse.from(i.getRequestor()),
                i.getAssignee() == null ? null : UserResponse.from(i.getAssignee()),
                i.getMilestone(), i.getCategory(), i.getKeywords(), i.getRootCause(),
                i.getCreatedAt(), i.getUpdatedAt());
    }
}
