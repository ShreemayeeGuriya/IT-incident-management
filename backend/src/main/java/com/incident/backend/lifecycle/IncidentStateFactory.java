package com.incident.backend.lifecycle;

import com.incident.backend.lifecycle.states.*;
import com.incident.backend.model.IncidentStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Returns the {@link IncidentState} object for a persisted {@link IncidentStatus} (Factory pattern).
 *
 * <p>State objects are stateless, so one shared instance per status is kept in an {@link EnumMap}.
 * Construction fails fast if any status has no state class, so adding a new status without
 * defining its lifecycle rules is caught at startup.</p>
 */
@Component
public class IncidentStateFactory {
    private final Map<IncidentStatus, IncidentState> states = new EnumMap<>(IncidentStatus.class);

    public IncidentStateFactory() {
        Stream.of(new ReportedState(), new ClassifiedState(), new PrioritizedState(), new AssignedState(),
                        new InvestigatingState(), new ResolvedState(), new VerifiedState(), new ClosedState())
                .forEach(state -> {
                    if (states.put(state.status(), state) != null) {
                        throw new IllegalStateException("Duplicate lifecycle state for " + state.status());
                    }
                });
        for (IncidentStatus status : IncidentStatus.values()) {
            if (!states.containsKey(status)) {
                throw new IllegalStateException("No lifecycle state defined for " + status);
            }
        }
    }

    public IncidentState stateOf(IncidentStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Incident status must not be null");
        }
        return states.get(status);
    }
}
