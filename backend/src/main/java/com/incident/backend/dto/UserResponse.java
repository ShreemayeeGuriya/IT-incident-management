package com.incident.backend.dto;

import com.incident.backend.model.Role;
import com.incident.backend.model.User;

import java.time.Instant;

public record UserResponse(Long id, String name, String email, Role role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId().longValue(), user.getName(), user.getEmail(),
                user.getRole(), user.getCreatedAt());
    }
}
