package com.incident.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SkillsRequest(@NotNull(message = "Skills list is required") List<String> skills) { }
