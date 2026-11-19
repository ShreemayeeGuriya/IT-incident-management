package com.incident.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InformationResponseCreateRequest(
        @NotBlank(message = "Response message is required")
        @Size(max = 3000, message = "Response message must be at most 3000 characters")
        String message) { }
