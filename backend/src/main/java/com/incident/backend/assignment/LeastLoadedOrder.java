package com.incident.backend.assignment;

import java.util.Comparator;

/** Shared tie-break order: lowest share of capacity used, then fewest open incidents, then lowest id. */
final class LeastLoadedOrder {
    private LeastLoadedOrder() { }

    static final Comparator<TechnicianCandidate> COMPARATOR = Comparator
            .comparingDouble(TechnicianCandidate::loadRatio)
            .thenComparingLong(TechnicianCandidate::activeIncidents)
            .thenComparing(TechnicianCandidate::id);
}
