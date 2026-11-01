package com.incident.backend.dto;

import com.incident.backend.model.Classification;
import com.incident.backend.model.Priority;
import com.incident.backend.model.Reproducibility;
import jakarta.validation.constraints.Size;

// Every field is optional: only the fields that are sent get changed.
public record IncidentUpdateRequest(
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,

        @Size(max = 100, message = "Component must be at most 100 characters")
        String component,

        Reproducibility reproducibility,

        // The fields below can only be changed by a TECHNICIAN or ADMIN
        Classification classification,
        Priority priority,

        @Size(max = 100, message = "Milestone must be at most 100 characters")
        String milestone,

        @Size(max = 100, message = "Category must be at most 100 characters")
        String category,

        @Size(max = 300, message = "Keywords must be at most 300 characters")
        String keywords,

        @Size(max = 2000, message = "Root cause must be at most 2000 characters")
        String rootCause) {
}
