package com.incident.backend.assignment;

import com.incident.backend.exception.IncidentNotEligibleException;
import com.incident.backend.exception.TechnicianUnavailableException;
import com.incident.backend.exception.TechnicianWorkloadExceededException;
import com.incident.backend.model.AvailabilityStatus;
import com.incident.backend.model.IncidentStatus;
import com.incident.backend.model.Role;
import com.incident.backend.model.Technician;
import com.incident.backend.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.incident.backend.assignment.Fixtures.candidate;
import static com.incident.backend.assignment.Fixtures.incident;
import static org.junit.jupiter.api.Assertions.*;

class AssignmentRulesTest {
    private final AssignmentRules rules = new AssignmentRules();

    @Test
    void availableTechnicianUnderTheLimitCanTakeWork() {
        var c = candidate(1, 4, 5);
        assertTrue(rules.canTake(c));
        assertDoesNotThrow(() -> rules.requireCanTake(c));
    }

    @Test
    void technicianAtMaximumWorkloadIsRejected() {
        var c = candidate(7, 5, 5);
        assertFalse(rules.canTake(c));
        var ex = assertThrows(TechnicianWorkloadExceededException.class, () -> rules.requireCanTake(c));
        assertEquals("TECHNICIAN_WORKLOAD_EXCEEDED", ex.getRule());
        assertTrue(ex.getMessage().contains("5 open incidents (maximum 5)"));
    }

    @ParameterizedTest
    @EnumSource(value = AvailabilityStatus.class, names = {"BUSY", "UNAVAILABLE"})
    void busyOrUnavailableTechnicianIsRejected(AvailabilityStatus status) {
        var c = candidate(3, 0, 5, status, Role.TECHNICIAN);
        assertFalse(rules.canTake(c));
        var ex = assertThrows(TechnicianUnavailableException.class, () -> rules.requireCanTake(c));
        assertTrue(ex.getMessage().contains(status.name()));
    }

    @Test
    void userWhoIsNoLongerATechnicianIsRejected() {
        var c = candidate(4, 0, 5, AvailabilityStatus.AVAILABLE, Role.EMPLOYEE);
        assertFalse(rules.canTake(c));
        assertThrows(TechnicianUnavailableException.class, () -> rules.requireCanTake(c));
    }

    @Test
    void onlyUnassignedPrioritizedIncidentsCanBeAutoAssigned() {
        assertDoesNotThrow(() -> rules.requireAutoAssignable(incident("Network", IncidentStatus.PRIORITIZED)));

        var withTechnician = incident("Network", IncidentStatus.PRIORITIZED);
        withTechnician.setAssignedTechnician(new Technician(new User()));
        assertThrows(IncidentNotEligibleException.class, () -> rules.requireAutoAssignable(withTechnician));
    }

    @ParameterizedTest
    @EnumSource(value = IncidentStatus.class, names = "PRIORITIZED", mode = EnumSource.Mode.EXCLUDE)
    void otherStatusesCannotBeAutoAssigned(IncidentStatus status) {
        var ex = assertThrows(IncidentNotEligibleException.class,
                () -> rules.requireAutoAssignable(incident("Network", status)));
        assertEquals("INCIDENT_NOT_ELIGIBLE", ex.getRule());
        assertTrue(ex.getMessage().contains(status.name()));
    }
}
