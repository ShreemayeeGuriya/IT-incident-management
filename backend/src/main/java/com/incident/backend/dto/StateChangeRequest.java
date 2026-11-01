package com.incident.backend.dto;

import com.incident.backend.model.IncidentState;
import com.incident.backend.model.Resolution;
import jakarta.validation.constraints.NotNull;

public record StateChangeRequest(
        @NotNull(message = "State is required") IncidentState state,
        Resolution resolution) {
}
