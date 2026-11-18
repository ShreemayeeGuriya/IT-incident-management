package com.incident.backend.dto;

import com.incident.backend.model.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;

public record IncidentCreateRequest(
        @NotBlank(message = "Description is required")
        @Size(max = 10000, message = "Description must be at most 10000 characters")
        String description,

        @NotNull(message = "Category is required") @Positive Integer categoryId,
        @NotNull(message = "Service is required") @Positive Integer serviceId,
        @Positive Integer slaPolicyId,

        @NotNull(message = "Impact is required") Impact impact,
        @NotNull(message = "Urgency is required") Urgency urgency,
        @NotNull(message = "Business criticality is required") BusinessCriticality businessCriticality,
        Priority priority) {
}
