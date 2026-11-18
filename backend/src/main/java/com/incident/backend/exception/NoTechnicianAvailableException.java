package com.incident.backend.exception;

/** No technician satisfies the active assignment strategy and the assignment rules. */
public class NoTechnicianAvailableException extends BusinessRuleViolationException {
    public NoTechnicianAvailableException(String strategy, String reason) {
        super("NO_TECHNICIAN_AVAILABLE", "No technician available using " + strategy + ": " + reason);
    }
}
