package com.incident.backend.exception;

import com.incident.backend.model.AvailabilityStatus;

/** The chosen technician is not AVAILABLE, or no longer has the TECHNICIAN role. */
public class TechnicianUnavailableException extends BusinessRuleViolationException {
    public TechnicianUnavailableException(Integer technicianId, AvailabilityStatus status) {
        super("TECHNICIAN_UNAVAILABLE", "Technician " + technicianId + " cannot take incidents (status: "
                + status + ")");
    }

    public TechnicianUnavailableException(Integer technicianId, String reason) {
        super("TECHNICIAN_UNAVAILABLE", "Technician " + technicianId + " cannot take incidents: " + reason);
    }
}
