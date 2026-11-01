package com.incident.backend.dto;

import com.incident.backend.model.Comment;

import java.time.Instant;

public record CommentResponse(Long id, Long incidentId, UserResponse author,
                              String text, Instant createdAt) {

    public static CommentResponse from(Comment c) {
        return new CommentResponse(c.getId(), c.getIncident().getId(),
                UserResponse.from(c.getAuthor()), c.getText(), c.getCreatedAt());
    }
}
