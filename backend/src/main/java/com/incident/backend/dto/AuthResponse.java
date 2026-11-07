package com.incident.backend.dto;

public record AuthResponse(String token, UserResponse user) {
}
