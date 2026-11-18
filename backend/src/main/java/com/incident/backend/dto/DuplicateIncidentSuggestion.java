package com.incident.backend.dto;

import com.incident.backend.model.Incident;
import com.incident.backend.model.IncidentStatus;

/** Read-only summary of an existing incident that may describe the same issue. */
public record DuplicateIncidentSuggestion(
        Long incidentId,
        String description,
        String category,
        String service,
        IncidentStatus status,
        double similarityScore) {

    public static DuplicateIncidentSuggestion from(Incident incident, double similarityScore) {
        return new DuplicateIncidentSuggestion(incident.getId().longValue(), incident.getDescription(),
                incident.getCategory().getName(), incident.getService().getName(),
                incident.getStatus(), similarityScore);
    }
}
