package com.incident.backend.dto;

import com.incident.backend.model.AffectedService;

public record ServiceResponse(Long id, String name, String description) {
    public static ServiceResponse from(AffectedService s) {
        return new ServiceResponse(s.getId().longValue(), s.getName(), s.getDescription());
    }
}
