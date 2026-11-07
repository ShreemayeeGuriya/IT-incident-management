package com.incident.backend.model;

/** Persisted state for one of an incident's independent SLA objectives. */
public enum SlaObjectiveStatus {
    NOT_STARTED,
    WITHIN_SLA,
    APPROACHING_BREACH,
    BREACHED,
    MET
}
