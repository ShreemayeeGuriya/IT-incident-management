package com.incident.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(
        @NotBlank(message = "Comment text is required")
        @Size(max = 3000, message = "Comment must be at most 3000 characters")
        String text) {
}
