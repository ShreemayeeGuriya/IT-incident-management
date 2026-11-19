package com.incident.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InformationRequestCreateRequest(
        @NotBlank(message = "Request message is required")
        @Size(max = 3000, message = "Request message must be at most 3000 characters")
        String message) { }
