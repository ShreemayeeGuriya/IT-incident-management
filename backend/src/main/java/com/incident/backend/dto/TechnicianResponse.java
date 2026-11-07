package com.incident.backend.dto;

import com.incident.backend.model.AvailabilityStatus;
import com.incident.backend.model.Technician;

public record TechnicianResponse(Long id, Long userId, String name, String email,
                                 Integer maxWorkload, AvailabilityStatus availabilityStatus,
                                 String experienceLevel) {
    public static TechnicianResponse from(Technician t) {
        return new TechnicianResponse(t.getId().longValue(), t.getUser().getId().longValue(),
                t.getUser().getName(), t.getUser().getEmail(), t.getMaxWorkload(),
                t.getAvailabilityStatus(), t.getExperienceLevel());
    }
}
