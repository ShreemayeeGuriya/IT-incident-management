package com.incident.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record StrategySelectionRequest(@NotBlank(message = "Strategy name is required") String strategy) { }
