package com.incident.backend.dto;

// assigneeId = null means "unassign"
public record AssignRequest(Long assigneeId) {
}
