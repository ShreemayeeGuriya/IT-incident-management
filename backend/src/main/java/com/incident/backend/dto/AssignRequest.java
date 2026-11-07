package com.incident.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

// technicianId = null means "unassign"
public record AssignRequest(@JsonAlias("assigneeId") Long technicianId) {
}
