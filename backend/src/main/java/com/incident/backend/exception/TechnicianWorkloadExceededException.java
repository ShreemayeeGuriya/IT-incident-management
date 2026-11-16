package com.incident.backend.exception;

/** The chosen technician already has as many open incidents as their maximum workload allows. */
public class TechnicianWorkloadExceededException extends BusinessRuleViolationException {
    public TechnicianWorkloadExceededException(Integer technicianId, long activeIncidents, int maxWorkload) {
        super("TECHNICIAN_WORKLOAD_EXCEEDED", "Technician " + technicianId + " already has " + activeIncidents
                + " open incidents (maximum " + maxWorkload + ")");
    }
}
