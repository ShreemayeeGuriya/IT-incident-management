package com.incident.backend.dto;

import com.incident.backend.strategy.NamedStrategy;
import com.incident.backend.strategy.StrategyRegistry;

import java.util.List;

/** Lists the strategies of one kind and marks the active one. */
public record StrategyResponse(String active, List<Option> available) {

    public record Option(String name, String description) { }

    public static StrategyResponse from(StrategyRegistry<? extends NamedStrategy> registry) {
        return new StrategyResponse(registry.active().name(), registry.available().stream()
                .map(s -> new Option(s.name(), s.description())).toList());
    }
}
