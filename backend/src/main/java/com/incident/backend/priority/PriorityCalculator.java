package com.incident.backend.priority;

import com.incident.backend.strategy.StrategyRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/** Shared active priority strategy and assessment entry point. */
@Component
public class PriorityCalculator {
    private final StrategyRegistry<PriorityStrategy> registry;

    public PriorityCalculator(List<PriorityStrategy> strategies,
                              @Value("${app.priority.strategy:" + WeightedScorePriorityStrategy.NAME + "}")
                              String defaultStrategy) {
        this.registry = new StrategyRegistry<>("priority", strategies, defaultStrategy);
    }

    public PriorityAssessment assess(PriorityInput input) {
        return registry.active().assess(input);
    }

    public StrategyRegistry<PriorityStrategy> strategies() {
        return registry;
    }
}
