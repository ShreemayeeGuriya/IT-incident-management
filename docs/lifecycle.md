# Incident lifecycle

Code: `backend/src/main/java/com/incident/backend/lifecycle/`

## Allowed transitions

| From | Allowed next | Entry condition |
|---|---|---|
| REPORTED | CLASSIFIED | none |
| CLASSIFIED | PRIORITIZED | none |
| PRIORITIZED | ASSIGNED | entering ASSIGNED needs a technician set |
| ASSIGNED | INVESTIGATING, PRIORITIZED | going back to PRIORITIZED releases the technician |
| INVESTIGATING | RESOLVED | entering RESOLVED needs resolution details |
| RESOLVED | VERIFIED, INVESTIGATING | going back to INVESTIGATING reopens the incident and clears `resolvedAt` |
| VERIFIED | CLOSED | none |
| CLOSED | none (final state) | none |

Any other move is rejected with `InvalidStateTransitionException`, which the API returns as **HTTP 409**:

```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Cannot move incident from REPORTED to CLOSED. Allowed next: [CLASSIFIED]",
  "fieldErrors": { "from": "REPORTED", "to": "CLOSED", "allowedNext": "[CLASSIFIED]" }
}
```

Requesting the status the incident already has is a no-op.

## Assignment rules

- Assigning a technician to a **PRIORITIZED** incident moves it to **ASSIGNED** automatically.
- Unassigning an **ASSIGNED** incident moves it back to **PRIORITIZED**.
- From INVESTIGATING onwards, the technician can be replaced but not removed.
- A CLOSED incident cannot be assigned.
- Assigning earlier (REPORTED or CLASSIFIED) sets the technician without changing the status.

## Design

| Class | Role | OOP concept |
|---|---|---|
| `IncidentState` | Contract for one lifecycle stage: `allowedNext()`, `canTransitionTo()`, `validateEntry()` | Interface, abstraction |
| `AbstractIncidentState` | Stores the status and an unmodifiable set of next statuses | Inheritance, encapsulation |
| `ReportedState` … `ClosedState` | One class per status; `AssignedState` and `ResolvedState` override `validateEntry()` | **State pattern**, polymorphism |
| `IncidentStateFactory` | Returns the shared state object for a status from an `EnumMap`; fails at startup if any status has no state class | **Factory pattern**, collections |
| `IncidentLifecycle` | The single place that changes an incident's status | Encapsulation, single responsibility |
| `InvalidStateTransitionException` | Custom exception carrying from, to and allowed statuses | Exception handling |

**Adding a new status:** add it to `IncidentStatus`, create one state class, and register it in `IncidentStateFactory`. If the registration is forgotten, the application refuses to start.
