# Domain logic: lifecycle, priority, assignment, business rules

This page covers the business logic of the system: the incident lifecycle, priority calculation, technician assignment and the rules around them. It is written so sections can go straight into the project report.

- Lifecycle details: [lifecycle.md](lifecycle.md)
- Class diagrams: [uml/domain-logic-class-diagram.md](uml/domain-logic-class-diagram.md)

## 1. Priority calculation

Priority is calculated from three factors on the incident, each LOW, MEDIUM or HIGH. Two interchangeable algorithms exist. An administrator can switch between them at runtime.

### WEIGHTED_SCORE (default)

Each factor is weighted 1 (LOW), 2 (MEDIUM) or 3 (HIGH), and the weights are multiplied:

`score = impact × urgency × business criticality` (range 1–27)

| Score | Priority | Example factors (impact, urgency, criticality) |
|---|---|---|
| 18–27 | **P1** Critical | HIGH, HIGH, HIGH (27) · HIGH, HIGH, MEDIUM (18) |
| 9–17 | **P2** High | HIGH, MEDIUM, MEDIUM (12) · HIGH, HIGH, LOW (9) |
| 4–8 | **P3** Medium | MEDIUM, MEDIUM, MEDIUM (8) · HIGH, MEDIUM, LOW (6) |
| 1–3 | **P4** Low | HIGH, LOW, LOW (3) · LOW, LOW, LOW (1) |

**Why multiply rather than add:** it follows the formula in the project brief, and a single LOW factor pulls the result down sharply. A HIGH-impact problem that is neither urgent nor business-critical (score 3) should not outrank a medium problem on every axis (score 8).

**Why these thresholds:** P1 needs at least two HIGH factors and nothing below MEDIUM, so only problems that are both widespread and urgent reach the 5-minute response SLA. All-MEDIUM lands on P3, the "normal" level.

### IMPACT_URGENCY_MATRIX

This is the standard ITIL matrix, with business criticality as a modifier:

| Impact \ Urgency | HIGH | MEDIUM | LOW |
|---|---|---|---|
| **HIGH** | P1 | P2 | P3 |
| **MEDIUM** | P2 | P3 | P4 |
| **LOW** | P3 | P4 | P4 |

HIGH criticality raises the result one level (never above P1), and LOW criticality lowers it one level (never below P4).

**Properties checked by tests (both strategies):** raising any single factor never lowers the priority; all-HIGH is P1 and all-LOW is P4; all four priorities can be reached.

**Applying a priority:** `POST /api/incidents/{id}/priority/recalculate` saves the result through the existing `IncidentService.update`. The SLA policy, deadlines and audit log follow automatically. Priority is fixed once an incident is RESOLVED.

## 1.1 Duplicate incident suggestions

Before creating a report, a signed-in user can send its description, category and affected service to `POST /api/incidents/duplicate-suggestions`. The endpoint is read-only: it returns a ranked JSON array with incident ID, description, category name, service name, current status and a similarity score from 0 to 1. No matches are represented by an empty array. It does not create, merge or update incidents.

Candidates are limited to incidents sharing the requested category or service, then ranked using token Dice similarity for descriptions (70% of the score), category match (15%) and service match (15%). Text must reach 0.35 similarity and the combined score must reach 0.55. Thus category/service coincidence alone cannot qualify. Matching ignores case, punctuation, common filler words and duplicate tokens. At most the 200 newest candidates are scored. Technicians and administrators can search all incidents; employees only receive suggestions for incidents they can already view (their own reports), preserving the existing incident visibility rule. This feature uses existing columns and needs no migration.

Example request body:

```json
{
  "description": "Email server unavailable for all users",
  "categoryId": 1,
  "serviceId": 1
}
```

Use the category and service IDs returned by the existing reference data/API in your database.

## 2. Technician assignment

Automatic assignment only applies to **PRIORITIZED** incidents that have no technician yet. It assigns through the existing `IncidentWorkflowService.assign`, so the incident moves to ASSIGNED and the usual audit entry and notifications are produced.

Every strategy only sees technicians who pass the **assignment rules** (section 3).

| Strategy | Picks | Notes |
|---|---|---|
| **SKILL_BASED** (default) | A technician with a skill matching the incident's category, e.g. "Network"; the least loaded among them | Skill names match category names, ignoring case. If nobody has the skill, the error names the missing skill. |
| **LOAD_BASED** | The technician using the smallest share of their capacity (`open incidents / max workload`) | Comparing the share rather than the raw count is fair to technicians with different limits. |
| **ROUND_ROBIN** | The next technician after the one chosen last time, wrapping around | Only a real assignment moves the turn on; a recommendation does not. The position resets on restart. |

Ties are broken by fewer open incidents, then the lower technician id, so results are deterministic and testable.

**Workload** counts incidents that are REPORTED, CLASSIFIED, PRIORITIZED, ASSIGNED or INVESTIGATING. RESOLVED incidents are excluded, because they only wait for the reporter to verify.

**Skills** are stored in the existing `skills` and `technician_skills` tables. Run `database/seed/002_skills.sql` after `001_reference_data.sql` to create one skill per category. Administrators then set skills per technician through the API.

## 3. Business rules

| Rule | Where | Exception (HTTP 409) |
|---|---|---|
| Only legal lifecycle steps; ASSIGNED needs a technician; RESOLVED needs resolution details | `IncidentLifecycle`, state classes | `InvalidStateTransitionException` |
| An in-progress incident can be reassigned but not left without a technician; a CLOSED incident cannot be assigned | `IncidentWorkflowService` | `InvalidStateTransitionException` |
| A technician must be AVAILABLE and still have the TECHNICIAN role | `AssignmentRules` | `TechnicianUnavailableException` |
| A technician cannot exceed their `max_workload` | `AssignmentRules` | `TechnicianWorkloadExceededException` |
| Auto-assignment only for PRIORITIZED incidents without a technician | `AssignmentRules` | `IncidentNotEligibleException` |
| Nobody suitable is free | `AssignmentService` | `NoTechnicianAvailableException` |
| Priority cannot be recalculated after RESOLVED | `PriorityService` | `IncidentNotEligibleException` |

The four business-rule exceptions share an abstract parent, `BusinessRuleViolationException`, which carries a short rule code such as `TECHNICIAN_WORKLOAD_EXCEEDED`. They all extend the existing `ConflictException`, so the existing `GlobalExceptionHandler` returns 409 without any changes.

## 4. API endpoints

| Method and path | Who | Purpose |
|---|---|---|
| `PATCH /api/incidents/{id}/status` | Technician, Admin | Change status (lifecycle enforced) |
| `POST /api/incidents/duplicate-suggestions` | Any authenticated user | Rank possible duplicates before reporting; employees are limited to their own visible incidents |
| `POST /api/priority/preview` | Any user | Priority for given factors, e.g. live on the report form |
| `GET /api/incidents/{id}/priority/assessment` | Viewer of the incident | What the priority should be, without changing it |
| `POST /api/incidents/{id}/priority/recalculate` | Technician, Admin | Recalculate and save priority |
| `GET /api/priority/strategies` · `PUT /api/priority/strategy` | Admin | List or switch priority algorithm |
| `GET /api/incidents/{id}/assignment/recommendation` | Technician, Admin | Who would be assigned, without assigning |
| `POST /api/incidents/{id}/auto-assign` | Technician, Admin | Assign with the active strategy |
| `GET /api/assignment/strategies` · `PUT /api/assignment/strategy` | Admin | List or switch assignment strategy |
| `GET /api/technicians/workload` | Technician, Admin | Open incidents, capacity and skills per technician |
| `GET /api/skills` | Any user | All skill names |
| `GET` / `PUT /api/technicians/{id}/skills` | Technician, Admin / Admin | View or replace a technician's skills |

Strategy switches take effect immediately but are not saved; the defaults come from `app.priority.strategy` (default `WEIGHTED_SCORE`) and `app.assignment.strategy` (default `SKILL_BASED`).

## 5. OOP concepts and design patterns

| Concept | Where it is used |
|---|---|
| **Encapsulation** | `IncidentLifecycle` is the only class that changes an incident's status. `AbstractIncidentState` exposes an unmodifiable set of next statuses. `StrategyRegistry` hides how the active strategy is stored. |
| **Abstraction** | `IncidentState`, `PriorityStrategy` and `AssignmentStrategy` describe *what* happens; services never depend on a concrete algorithm. |
| **Inheritance** | 8 state classes extend `AbstractIncidentState`. 4 exceptions extend `BusinessRuleViolationException`, which extends `ConflictException`. Each is a true "is-a" relationship. |
| **Polymorphism** | `AssignedState` and `ResolvedState` override `validateEntry()`. Services call `strategy.assess()` or `strategy.choose()` without knowing which implementation runs. |
| **Interfaces** | `IncidentState`, `NamedStrategy`, `PriorityStrategy` (extends `NamedStrategy`), `AssignmentStrategy` (extends `NamedStrategy`) |
| **Generics** | `StrategyRegistry<S extends NamedStrategy>` serves both kinds of strategy with one implementation. |
| **Composition** | `PriorityService` and `AssignmentService` each own a `StrategyRegistry`; `IncidentStateFactory` owns the state objects. |
| **Association / aggregation** | `TechnicianCandidate` wraps a `Technician`; `AssignmentService` uses `AssignmentRules`, `TechnicianProfileRepository` and `IncidentWorkflowService`. |
| **Immutability** | `PriorityInput`, `PriorityAssessment` and `TechnicianCandidate` are records with defensive copies. |
| **Collections** | `EnumMap` for state lookup, `EnumSet` for allowed transitions, `LinkedHashMap` to keep strategy order, `AtomicReference` for thread-safe switching. |
| **Exception handling** | Specific custom exceptions for each broken rule, mapped to HTTP status codes in one place. |
| **State pattern** | Incident lifecycle: one class per status. |
| **Strategy pattern** | Two priority algorithms and three assignment algorithms, switchable at runtime. |
| **Factory pattern** | `IncidentStateFactory` builds and returns state objects; fails at startup if a status has no state. |
| **Repository pattern** | `TechnicianProfileRepository` hides the SQL for skills and workload counts. |

**Extending the system:** a new priority or assignment algorithm is one new class annotated `@Component`. It appears in the strategy list automatically, and nothing else changes. A new incident status is one state class plus one line in `IncidentStateFactory`.

## 6. Tests

| Test class | Type | Covers |
|---|---|---|
| `IncidentLifecycleTest` | Unit | All 56 status pairs, guards, factory, happy path, reopen |
| `PriorityStrategyTest` | Unit | Worked examples, monotonicity, all priorities reachable |
| `StrategyRegistryTest` | Unit | Default, switching, unknown names, startup misconfiguration |
| `AssignmentStrategyTest` | Unit | Each strategy's choice and tie-breaks; round robin turn-taking |
| `AssignmentRulesTest` | Unit | Availability, workload, role, auto-assign eligibility |
| `PostgresIncidentLifecycleIT` | Integration | Lifecycle through service, database, audit and REST API |
| `PostgresPriorityAssignmentIT` | Integration | Recalculation and SLA, strategies end to end, rules, permissions, API errors |

Run the integration tests with `./mvnw -Dpostgres.integration=true '-Dtest=Postgres*IT' test` against a database prepared as described in `database/README.md`, with `002_skills.sql` applied as well.
