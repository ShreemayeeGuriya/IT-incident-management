package com.incident.backend.strategy;

import com.incident.backend.exception.BadRequestException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds every implementation of one kind of strategy and tracks which one is active.
 *
 * <p>Generic so the same mechanism serves priority and assignment strategies. Switching is
 * thread-safe and takes effect immediately for the next request; it is not persisted, so a restart
 * returns to the configured default.</p>
 *
 * @param <S> the strategy type
 */
public class StrategyRegistry<S extends NamedStrategy> {
    private final String kind;
    private final Map<String, S> strategies;
    private final AtomicReference<S> active = new AtomicReference<>();

    public StrategyRegistry(String kind, List<S> implementations, String defaultName) {
        this.kind = kind;
        Map<String, S> byName = new LinkedHashMap<>();
        for (S strategy : implementations) {
            if (byName.put(key(strategy.name()), strategy) != null) {
                throw new IllegalStateException("Duplicate " + kind + " strategy: " + strategy.name());
            }
        }
        if (byName.isEmpty()) {
            throw new IllegalStateException("No " + kind + " strategies are registered");
        }
        this.strategies = Collections.unmodifiableMap(byName);
        S initial = strategies.get(key(defaultName));
        if (initial == null) {
            throw new IllegalStateException("Unknown default " + kind + " strategy '" + defaultName
                    + "'. Available: " + strategies.keySet());
        }
        active.set(initial);
    }

    public S active() {
        return active.get();
    }

    /** All registered strategies, in registration order. */
    public List<S> available() {
        return List.copyOf(strategies.values());
    }

    /** Makes the named strategy active. */
    public S select(String name) {
        S strategy = name == null ? null : strategies.get(key(name));
        if (strategy == null) {
            throw new BadRequestException("Unknown " + kind + " strategy '" + name + "'. Available: "
                    + strategies.keySet());
        }
        active.set(strategy);
        return strategy;
    }

    private static String key(String name) {
        return name.trim().toUpperCase(Locale.ROOT);
    }
}
