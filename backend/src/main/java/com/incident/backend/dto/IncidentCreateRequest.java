package com.incident.backend.dto;

import com.incident.backend.model.Classification;
import com.incident.backend.model.Reproducibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IncidentCreateRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @NotBlank(message = "Description is required")
        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,

        @Size(max = 100, message = "Component must be at most 100 characters")
        String component,

        Classification classification,
        Reproducibility reproducibility,

        @Size(max = 100, message = "Milestone must be at most 100 characters")
        String milestone,

        @Size(max = 100, message = "Category must be at most 100 characters")
        String category,

        @Size(max = 300, message = "Keywords must be at most 300 characters")
        String keywords) {
}
