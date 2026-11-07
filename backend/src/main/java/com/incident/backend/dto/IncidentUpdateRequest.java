package com.incident.backend.dto;

import com.incident.backend.model.*;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// Every field is optional: only the fields that are sent get changed.
public record IncidentUpdateRequest(
        @Size(max = 10000, message = "Description must be at most 10000 characters")
        String description,

        @Positive Integer categoryId,
        @Positive Integer serviceId,
        @Positive Integer slaPolicyId,
        Impact impact,
        Urgency urgency,
        BusinessCriticality businessCriticality,
        Priority priority,
        @Size(max = 10000) String investigationDetails,
        @Size(max = 10000) String resolutionDetails,
        @Size(max = 10000) String closureDetails) {
}
