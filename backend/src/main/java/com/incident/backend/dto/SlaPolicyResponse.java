package com.incident.backend.dto;

import com.incident.backend.model.Priority;
import com.incident.backend.model.SlaPolicy;

public record SlaPolicyResponse(Long id, String name, Priority priority,
                                Integer responseTimeMinutes, Integer resolutionTimeMinutes) {
    public static SlaPolicyResponse from(SlaPolicy p) {
        return new SlaPolicyResponse(p.getId().longValue(), p.getName(), p.getPriority(),
                p.getResponseTimeMinutes(), p.getResolutionTimeMinutes());
    }
}
