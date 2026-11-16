package com.incident.backend.dto;

import com.incident.backend.priority.PriorityAssessment;
import com.incident.backend.priority.PriorityService;

public record PriorityRecalculationResponse(PriorityAssessment assessment, boolean changed,
                                            IncidentResponse incident) {
    public static PriorityRecalculationResponse from(PriorityService.Recalculation r) {
        return new PriorityRecalculationResponse(r.assessment(), r.changed(), r.incident());
    }
}
