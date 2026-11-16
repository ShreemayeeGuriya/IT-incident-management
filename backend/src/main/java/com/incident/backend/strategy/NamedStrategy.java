package com.incident.backend.strategy;

/** Common contract for interchangeable algorithms that can be listed and selected by name. */
public interface NamedStrategy {

    /** Stable identifier used in configuration and the API, e.g. {@code LOAD_BASED}. */
    String name();

    /** Human-readable explanation shown to administrators. */
    String description();
}
