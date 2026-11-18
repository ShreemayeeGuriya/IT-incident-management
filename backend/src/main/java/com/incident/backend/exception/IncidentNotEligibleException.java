package com.incident.backend.exception;

import com.incident.backend.model.IncidentStatus;

/** The incident is in a status where the requested operation is not allowed. */
public class IncidentNotEligibleException extends BusinessRuleViolationException {
    public IncidentNotEligibleException(String operation, IncidentStatus status, String reason) {
        super("INCIDENT_NOT_ELIGIBLE", "Cannot " + operation + " while the incident is " + status + ": " + reason);
    }
}
