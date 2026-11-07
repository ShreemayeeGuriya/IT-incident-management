package com.incident.backend.dto;

import com.incident.backend.model.Category;

public record CategoryResponse(Long id, String name, String description) {
    public static CategoryResponse from(Category c) {
        return new CategoryResponse(c.getId().longValue(), c.getName(), c.getDescription());
    }
}
