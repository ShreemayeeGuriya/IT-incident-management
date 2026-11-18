package com.incident.backend.assignment;

import com.incident.backend.exception.IncidentNotEligibleException;
import com.incident.backend.exception.TechnicianUnavailableException;
import com.incident.backend.exception.TechnicianWorkloadExceededException;
import com.incident.backend.model.AvailabilityStatus;
import com.incident.backend.model.Incident;
import com.incident.backend.model.IncidentStatus;
import com.incident.backend.model.Role;
import org.springframework.stereotype.Component;

/**
 * Business rules for giving an incident to a technician (Stage 4). Kept separate from the
 * strategies so every strategy obeys the same rules.
 */
@Component
public class AssignmentRules {

    /** True if the technician may take one more incident. */
    public boolean canTake(TechnicianCandidate candidate) {
        return isActiveTechnician(candidate)
                && candidate.technician().getAvailabilityStatus() == AvailabilityStatus.AVAILABLE
                && candidate.hasCapacity();
    }

    /**
     * Throws the specific reason the technician may not take another incident.
     *
     * @throws TechnicianUnavailableException      if not AVAILABLE or no longer a technician
     * @throws TechnicianWorkloadExceededException if already at maximum workload
     */
    public void requireCanTake(TechnicianCandidate candidate) {
        if (!isActiveTechnician(candidate)) {
            throw new TechnicianUnavailableException(candidate.id(), "the user no longer has the TECHNICIAN role");
        }
        AvailabilityStatus availability = candidate.technician().getAvailabilityStatus();
        if (availability != AvailabilityStatus.AVAILABLE) {
            throw new TechnicianUnavailableException(candidate.id(), availability);
        }
        if (!candidate.hasCapacity()) {
            throw new TechnicianWorkloadExceededException(candidate.id(), candidate.activeIncidents(),
                    candidate.maxWorkload());
        }
    }

    /**
     * Automatic assignment is for incidents that have been prioritized and have no technician yet.
     *
     * @throws IncidentNotEligibleException otherwise
     */
    public void requireAutoAssignable(Incident incident) {
        IncidentStatus status = incident.getStatus();
        switch (status) {
            case PRIORITIZED -> {
                if (incident.getAssignedTechnician() != null) {
                    throw new IncidentNotEligibleException("auto-assign", status,
                            "a technician is already set; move it to ASSIGNED instead");
                }
            }
            case REPORTED, CLASSIFIED -> throw new IncidentNotEligibleException("auto-assign", status,
                    "classify and prioritize the incident first");
            default -> throw new IncidentNotEligibleException("auto-assign", status,
                    "the incident already has a technician or is finished");
        }
    }

    private static boolean isActiveTechnician(TechnicianCandidate candidate) {
        return candidate.technician().getUser().getRole() == Role.TECHNICIAN;
    }
}
