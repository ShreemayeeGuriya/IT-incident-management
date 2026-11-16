package com.incident.backend.lifecycle;

import com.incident.backend.exception.InvalidStateTransitionException;
import com.incident.backend.model.Incident;
import com.incident.backend.model.IncidentStatus;
import com.incident.backend.model.Technician;
import com.incident.backend.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.incident.backend.model.IncidentStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class IncidentLifecycleTest {

    /** The agreed lifecycle. Every pair not listed here must be rejected. */
    private static final Map<IncidentStatus, Set<IncidentStatus>> EXPECTED = Map.of(
            REPORTED, EnumSet.of(CLASSIFIED),
            CLASSIFIED, EnumSet.of(PRIORITIZED),
            PRIORITIZED, EnumSet.of(ASSIGNED),
            ASSIGNED, EnumSet.of(INVESTIGATING, PRIORITIZED),
            INVESTIGATING, EnumSet.of(RESOLVED),
            RESOLVED, EnumSet.of(VERIFIED, INVESTIGATING),
            VERIFIED, EnumSet.of(CLOSED),
            CLOSED, EnumSet.noneOf(IncidentStatus.class));

    private IncidentStateFactory factory;
    private IncidentLifecycle lifecycle;

    @BeforeEach
    void setUp() {
        factory = new IncidentStateFactory();
        lifecycle = new IncidentLifecycle(factory);
    }

    /** An incident that satisfies every guard condition, so only the transition table is tested. */
    private static Incident readyIncident(IncidentStatus status) {
        Incident incident = new Incident();
        incident.setStatus(status);
        incident.setAssignedTechnician(new Technician(new User()));
        incident.setResolutionDetails("Restarted the payment service");
        return incident;
    }

    static Stream<Arguments> allPairs() {
        List<Arguments> pairs = new ArrayList<>();
        for (IncidentStatus from : IncidentStatus.values()) {
            for (IncidentStatus to : IncidentStatus.values()) {
                if (from != to) pairs.add(Arguments.of(from, to));
            }
        }
        return pairs.stream();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allPairs")
    @DisplayName("every transition is allowed exactly when the lifecycle table says so")
    void transitionTableIsEnforced(IncidentStatus from, IncidentStatus to) {
        Incident incident = readyIncident(from);
        if (EXPECTED.get(from).contains(to)) {
            assertEquals(from, lifecycle.transition(incident, to));
            assertEquals(to, incident.getStatus());
        } else {
            InvalidStateTransitionException ex = assertThrows(InvalidStateTransitionException.class,
                    () -> lifecycle.transition(incident, to));
            assertEquals(from, ex.getFrom());
            assertEquals(to, ex.getTo());
            assertEquals(EXPECTED.get(from), ex.getAllowed());
            assertEquals(from, incident.getStatus(), "a rejected move must not change the status");
        }
    }

    @ParameterizedTest
    @EnumSource(IncidentStatus.class)
    void factoryReturnsAStateForEveryStatus(IncidentStatus status) {
        IncidentState state = factory.stateOf(status);
        assertEquals(status, state.status());
        assertEquals(EXPECTED.get(status), state.allowedNext());
    }

    @Test
    void factoryReturnsSharedInstances() {
        assertSame(factory.stateOf(REPORTED), factory.stateOf(REPORTED));
    }

    @Test
    void factoryRejectsNullStatus() {
        assertThrows(IllegalArgumentException.class, () -> factory.stateOf(null));
    }

    @Test
    void allowedNextCannotBeModified() {
        assertThrows(UnsupportedOperationException.class,
                () -> factory.stateOf(REPORTED).allowedNext().add(CLOSED));
    }

    @Test
    void onlyClosedIsTerminal() {
        for (IncidentStatus status : IncidentStatus.values()) {
            assertEquals(status == CLOSED, factory.stateOf(status).isTerminal(), status.name());
        }
    }

    @Test
    @DisplayName("the full happy path from REPORTED to CLOSED succeeds")
    void happyPath() {
        Incident incident = readyIncident(REPORTED);
        for (IncidentStatus next : List.of(CLASSIFIED, PRIORITIZED, ASSIGNED, INVESTIGATING, RESOLVED,
                VERIFIED, CLOSED)) {
            lifecycle.transition(incident, next);
        }
        assertEquals(CLOSED, incident.getStatus());
    }

    @Test
    @DisplayName("a failed fix can be reopened and resolved again")
    void reopenAfterResolution() {
        Incident incident = readyIncident(RESOLVED);
        lifecycle.transition(incident, INVESTIGATING);
        lifecycle.transition(incident, RESOLVED);
        assertEquals(RESOLVED, incident.getStatus());
    }

    @Test
    void reportedCannotJumpToClosed() {
        Incident incident = readyIncident(REPORTED);
        InvalidStateTransitionException ex = assertThrows(InvalidStateTransitionException.class,
                () -> lifecycle.transition(incident, CLOSED));
        assertTrue(ex.getMessage().contains("REPORTED"));
        assertTrue(ex.getMessage().contains("CLOSED"));
    }

    @Test
    void enteringAssignedRequiresATechnician() {
        Incident incident = readyIncident(PRIORITIZED);
        incident.setAssignedTechnician(null);
        InvalidStateTransitionException ex = assertThrows(InvalidStateTransitionException.class,
                () -> lifecycle.transition(incident, ASSIGNED));
        assertTrue(ex.getMessage().contains("technician"));
        assertEquals(PRIORITIZED, incident.getStatus());
    }

    @Test
    void enteringResolvedRequiresResolutionDetails() {
        for (String details : new String[]{null, "", "   "}) {
            Incident incident = readyIncident(INVESTIGATING);
            incident.setResolutionDetails(details);
            InvalidStateTransitionException ex = assertThrows(InvalidStateTransitionException.class,
                    () -> lifecycle.transition(incident, RESOLVED));
            assertTrue(ex.getMessage().contains("resolution details"));
            assertEquals(INVESTIGATING, incident.getStatus());
        }
    }

    @Test
    void nullTargetIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> lifecycle.transition(readyIncident(REPORTED), null));
    }

    @Test
    void canTransitionDoesNotChangeTheIncident() {
        Incident incident = readyIncident(REPORTED);
        assertTrue(lifecycle.canTransition(incident, CLASSIFIED));
        assertFalse(lifecycle.canTransition(incident, CLOSED));
        assertEquals(REPORTED, incident.getStatus());
    }
}
