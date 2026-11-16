package com.incident.backend.strategy;

import com.incident.backend.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StrategyRegistryTest {
    record Fake(String name) implements NamedStrategy {
        public String description() { return "fake " + name; }
    }

    private final Fake a = new Fake("ALPHA");
    private final Fake b = new Fake("BETA");

    @Test
    void startsWithTheDefaultAndSwitchesByNameIgnoringCase() {
        StrategyRegistry<Fake> registry = new StrategyRegistry<>("test", List.of(a, b), "alpha");
        assertSame(a, registry.active());
        assertSame(b, registry.select(" beta "));
        assertSame(b, registry.active());
        assertEquals(List.of(a, b), registry.available());
    }

    @Test
    void unknownSelectionIsABadRequestAndKeepsTheCurrentStrategy() {
        StrategyRegistry<Fake> registry = new StrategyRegistry<>("test", List.of(a, b), "ALPHA");
        BadRequestException ex = assertThrows(BadRequestException.class, () -> registry.select("GAMMA"));
        assertTrue(ex.getMessage().contains("ALPHA"));
        assertThrows(BadRequestException.class, () -> registry.select(null));
        assertSame(a, registry.active());
    }

    @Test
    void misconfigurationFailsAtStartup() {
        assertThrows(IllegalStateException.class, () -> new StrategyRegistry<>("test", List.of(a), "BETA"));
        assertThrows(IllegalStateException.class, () -> new StrategyRegistry<>("test", List.of(a, new Fake("alpha")), "ALPHA"));
        assertThrows(IllegalStateException.class, () -> new StrategyRegistry<Fake>("test", List.of(), "ALPHA"));
    }

    @Test
    void availableListCannotBeModified() {
        StrategyRegistry<Fake> registry = new StrategyRegistry<>("test", List.of(a, b), "ALPHA");
        assertThrows(UnsupportedOperationException.class, () -> registry.available().add(a));
    }
}
