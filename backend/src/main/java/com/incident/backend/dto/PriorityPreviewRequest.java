package com.incident.backend.dto;

import com.incident.backend.model.BusinessCriticality;
import com.incident.backend.model.Impact;
import com.incident.backend.model.Urgency;
import jakarta.validation.constraints.NotNull;

/** Factors for a priority preview, e.g. while an employee fills in the report form. */
public record PriorityPreviewRequest(
        @NotNull(message = "Impact is required") Impact impact,
        @NotNull(message = "Urgency is required") Urgency urgency,
        @NotNull(message = "Business criticality is required") BusinessCriticality businessCriticality) { }
