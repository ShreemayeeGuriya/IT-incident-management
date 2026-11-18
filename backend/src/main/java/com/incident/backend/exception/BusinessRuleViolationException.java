package com.incident.backend.exception;

/**
 * Base class for requests that are well-formed but break a business rule (HTTP 409).
 * Subclasses name the specific rule so callers and tests can tell them apart.
 */
public abstract class BusinessRuleViolationException extends ConflictException {
    private final String rule;

    protected BusinessRuleViolationException(String rule, String message) {
        super(message);
        this.rule = rule;
    }

    /** Short identifier of the rule that was broken, e.g. {@code TECHNICIAN_UNAVAILABLE}. */
    public String getRule() {
        return rule;
    }
}
