package com.incident.backend.dto;

import com.incident.backend.model.IncidentStatus;
import jakarta.validation.constraints.NotNull;

public record StateChangeRequest(@NotNull(message = "Status is required") IncidentStatus status) { }
