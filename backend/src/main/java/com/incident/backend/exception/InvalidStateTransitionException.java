package com.incident.backend.exception;

import com.incident.backend.model.IncidentStatus;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Thrown when an incident is asked to move to a status the lifecycle does not allow. Maps to HTTP 409. */
public class InvalidStateTransitionException extends ConflictException {
    private final IncidentStatus from;
    private final IncidentStatus to;
    private final Set<IncidentStatus> allowed;

    private InvalidStateTransitionException(String message, IncidentStatus from, IncidentStatus to,
                                            Set<IncidentStatus> allowed) {
        super(message);
        this.from = from;
        this.to = to;
        this.allowed = allowed.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(allowed));
    }

    /** The target status is not a legal next step from the current status. */
    public static InvalidStateTransitionException notAllowed(IncidentStatus from, IncidentStatus to,
                                                             Set<IncidentStatus> allowed) {
        String options = allowed.isEmpty() ? "none (" + from + " is a final state)" : allowed.toString();
        return new InvalidStateTransitionException(
                "Cannot move incident from " + from + " to " + to + ". Allowed next: " + options,
                from, to, allowed);
    }

    /** The step is legal, but the incident does not yet meet the conditions to enter the target status. */
    public static InvalidStateTransitionException guardFailed(IncidentStatus from, IncidentStatus to, String reason) {
        return new InvalidStateTransitionException(
                "Cannot move incident from " + from + " to " + to + ": " + reason,
                from, to, Set.of(to));
    }

    /** Removing the technician would leave an in-progress incident without an owner. */
    public static InvalidStateTransitionException cannotUnassign(IncidentStatus status) {
        return new InvalidStateTransitionException(
                "Cannot unassign the technician while the incident is " + status
                        + ". Reassign it to another technician instead", status, status, Set.of());
    }

    /** A final-state incident cannot be given a new technician. */
    public static InvalidStateTransitionException cannotAssign(IncidentStatus status) {
        return new InvalidStateTransitionException(
                "Cannot assign a technician to an incident that is " + status, status, status, Set.of());
    }

    public IncidentStatus getFrom() { return from; }
    public IncidentStatus getTo() { return to; }
    public Set<IncidentStatus> getAllowed() { return allowed; }
}
