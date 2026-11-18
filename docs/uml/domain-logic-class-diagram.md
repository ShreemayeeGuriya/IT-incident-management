# Class diagram: domain logic (Student 3)

Covers the incident lifecycle, priority calculation, technician assignment and business-rule exceptions.
GitHub renders the Mermaid blocks below as diagrams. Entities from `model/` are shown with only the members used here.

## 1. Incident lifecycle (State + Factory)

```mermaid
classDiagram
    direction LR
    class IncidentState {
        <<interface>>
        +status() IncidentStatus
        +allowedNext() Set~IncidentStatus~
        +canTransitionTo(IncidentStatus) boolean
        +isTerminal() boolean
        +validateEntry(Incident) void
    }
    class AbstractIncidentState {
        <<abstract>>
        -status: IncidentStatus
        -allowedNext: Set~IncidentStatus~
    }
    class ReportedState
    class ClassifiedState
    class PrioritizedState
    class AssignedState {
        +validateEntry(Incident) void
    }
    class InvestigatingState
    class ResolvedState {
        +validateEntry(Incident) void
    }
    class VerifiedState
    class ClosedState
    class IncidentStateFactory {
        -states: EnumMap~IncidentStatus, IncidentState~
        +stateOf(IncidentStatus) IncidentState
    }
    class IncidentLifecycle {
        +currentState(Incident) IncidentState
        +canTransition(Incident, IncidentStatus) boolean
        +transition(Incident, IncidentStatus) IncidentStatus
    }
    class IncidentWorkflowService {
        +assign(Long, Long, User) IncidentResponse
        +changeState(Long, StateChangeRequest, User) IncidentResponse
    }
    class Incident {
        -status: IncidentStatus
        -assignedTechnician: Technician
        -resolutionDetails: String
    }

    IncidentState <|.. AbstractIncidentState
    AbstractIncidentState <|-- ReportedState
    AbstractIncidentState <|-- ClassifiedState
    AbstractIncidentState <|-- PrioritizedState
    AbstractIncidentState <|-- AssignedState
    AbstractIncidentState <|-- InvestigatingState
    AbstractIncidentState <|-- ResolvedState
    AbstractIncidentState <|-- VerifiedState
    AbstractIncidentState <|-- ClosedState
    IncidentStateFactory *-- "8" IncidentState : creates and owns
    IncidentLifecycle --> IncidentStateFactory
    IncidentLifecycle ..> Incident : changes status
    IncidentWorkflowService --> IncidentLifecycle
    IncidentLifecycle ..> InvalidStateTransitionException : throws
```

## 2. Priority calculation (Strategy)

```mermaid
classDiagram
    direction LR
    class NamedStrategy {
        <<interface>>
        +name() String
        +description() String
    }
    class StrategyRegistry~S~ {
        -strategies: Map~String, S~
        -active: AtomicReference~S~
        +active() S
        +available() List~S~
        +select(String) S
    }
    class PriorityStrategy {
        <<interface>>
        +assess(PriorityInput) PriorityAssessment
    }
    class WeightedScorePriorityStrategy
    class ImpactUrgencyMatrixPriorityStrategy
    class PriorityInput {
        <<record>>
        +impact: Impact
        +urgency: Urgency
        +businessCriticality: BusinessCriticality
    }
    class PriorityAssessment {
        <<record>>
        +priority: Priority
        +score: int
        +strategy: String
        +explanation: String
    }
    class PriorityService {
        +assess(PriorityInput) PriorityAssessment
        +assess(Long, User) PriorityAssessment
        +recalculate(Long, User) Recalculation
    }
    class IncidentService {
        +update(Long, IncidentUpdateRequest, User) IncidentResponse
    }

    NamedStrategy <|-- PriorityStrategy
    PriorityStrategy <|.. WeightedScorePriorityStrategy
    PriorityStrategy <|.. ImpactUrgencyMatrixPriorityStrategy
    PriorityStrategy ..> PriorityInput
    PriorityStrategy ..> PriorityAssessment
    PriorityService *-- StrategyRegistry : priority strategies
    StrategyRegistry o-- "1..*" NamedStrategy
    PriorityService --> IncidentService : saves new priority, SLA follows
    PriorityController --> PriorityService
```

## 3. Technician assignment (Strategy) and business rules

```mermaid
classDiagram
    direction LR
    class AssignmentStrategy {
        <<interface>>
        +choose(Incident, List~TechnicianCandidate~) Optional~TechnicianCandidate~
        +onAssigned(TechnicianCandidate) void
        +noMatchReason(Incident) String
    }
    class SkillBasedAssignmentStrategy
    class LoadBasedAssignmentStrategy
    class RoundRobinAssignmentStrategy {
        -lastChosenId: Integer
    }
    class TechnicianCandidate {
        <<record>>
        +technician: Technician
        +activeIncidents: long
        +skills: Set~String~
        +hasCapacity() boolean
        +loadRatio() double
        +hasSkill(String) boolean
    }
    class AssignmentRules {
        +canTake(TechnicianCandidate) boolean
        +requireCanTake(TechnicianCandidate) void
        +requireAutoAssignable(Incident) void
    }
    class AssignmentService {
        +recommend(Long) Recommendation
        +autoAssign(Long, User) Result
        +requireCanTake(Integer) void
        +workload() List~TechnicianSummary~
    }
    class TechnicianProfileRepository {
        +activeIncidentCounts() Map
        +skillsByTechnician() Map
        +replaceSkills(Integer, Collection) void
    }
    class Technician {
        -maxWorkload: Integer
        -availabilityStatus: AvailabilityStatus
    }

    NamedStrategy <|-- AssignmentStrategy
    AssignmentStrategy <|.. SkillBasedAssignmentStrategy
    AssignmentStrategy <|.. LoadBasedAssignmentStrategy
    AssignmentStrategy <|.. RoundRobinAssignmentStrategy
    AssignmentStrategy ..> TechnicianCandidate
    TechnicianCandidate o-- Technician
    AssignmentService *-- StrategyRegistry : assignment strategies
    AssignmentService --> AssignmentRules
    AssignmentService --> TechnicianProfileRepository
    AssignmentService --> IncidentWorkflowService : performs the assignment
    AssignmentController --> AssignmentService
```

## 4. Exceptions

```mermaid
classDiagram
    direction TB
    RuntimeException <|-- ConflictException
    ConflictException <|-- InvalidStateTransitionException
    ConflictException <|-- BusinessRuleViolationException
    BusinessRuleViolationException <|-- TechnicianUnavailableException
    BusinessRuleViolationException <|-- TechnicianWorkloadExceededException
    BusinessRuleViolationException <|-- NoTechnicianAvailableException
    BusinessRuleViolationException <|-- IncidentNotEligibleException
    class InvalidStateTransitionException {
        -from: IncidentStatus
        -to: IncidentStatus
        -allowed: Set~IncidentStatus~
    }
    class BusinessRuleViolationException {
        <<abstract>>
        -rule: String
    }
```

All of these return **HTTP 409 Conflict** through the existing `GlobalExceptionHandler`.
