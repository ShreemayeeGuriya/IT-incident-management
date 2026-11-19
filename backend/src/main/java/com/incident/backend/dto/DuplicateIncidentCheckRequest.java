package com.incident.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Input used to find similar existing incidents before creating a new one. */
public record DuplicateIncidentCheckRequest(
        @NotBlank(message = "Description is required")
        @Size(max = 10000, message = "Description must be at most 10000 characters")
        String description,
        @NotNull(message = "Category is required") @Positive(message = "Category ID must be positive") Integer categoryId,
        @NotNull(message = "Service is required") @Positive(message = "Service ID must be positive") Integer serviceId) { }
