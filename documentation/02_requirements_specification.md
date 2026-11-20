# 2. Requirements specification

## Source and interpretation

The assignment source is the supplied **OOP_S1_2026-2027_Project.pdf**. This table separates target requirements from observed implementation, based on controllers, services, entities, pages, SQL, and test sources.

## Functional requirements and status

| ID | Requirement | Status |
|---|---|---|
| FR-01 | Register/login | Implemented; JWT bearer authentication |
| FR-02 | Report with description, category, service, impact, urgency, criticality | Implemented; lookups come from database API |
| FR-03 | Lifecycle with guarded transitions | Implemented using state objects; creation auto-classifies/prioritizes |
| FR-04 | Configurable priority algorithm | Implemented: weighted score and impact/urgency matrix |
| FR-05 | At least two assignment strategies | Implemented: skill-based, load-based, round-robin |
| FR-06 | Technician availability/workload eligibility | Implemented in assignment rules |
| FR-07 | Audit history and comments | Implemented as separate records |
| FR-08 | Search/filter incidents | Implemented with status, priority, technician and text filters; paged API |
| FR-09 | Role dashboards and workload view | Implemented; dashboard stats use a fetched incident page (typically latest 100) |
| FR-10 | Track SLA response/resolution | Implemented with policy-based deadlines and separate objective statuses |
| FR-11 | Escalate SLA breach | Implemented with persisted escalation record and notification events |
| FR-12 | Two notification mechanisms | Partial: in-app plus simulated email attempts; no live provider |
| FR-13 | Find similar incidents | Implemented as ranked token/synonym heuristic; not ML |
| FR-14 | Admin configures strategies | Implemented |
| FR-15 | Admin manages users/roles and technician skills | Implemented; skills persist and include seeded category skills |
| FR-16 | Admin creates/edits categories and services | Partial: display-only lookup page; no category/service write API |
| FR-17 | Dedicated ask-for-information and feedback workflow | Not implemented |
| FR-18 | Automatic category suggestion, root-cause correlation, historical resolution recommendation | Not implemented |
| FR-19 | Downloadable incident reporting | Not implemented; dashboard summaries only |

## Non-functional requirements

- Security: BCrypt password hashing, stateless JWT, role checks and employee ownership enforcement.
- Persistence: PostgreSQL schema, migrations/seeds, JPA and JDBC repositories.
- Consistency: transactional service operations; application events drive audit/notification records.
- Usability: role-specific React views, dynamic reference selectors, status/priority indicators, paged lists.
- Maintainability: separated controllers, DTOs, services, models, repositories, strategies and lifecycle states.
- Testability: Maven unit/API and opt-in PostgreSQL IT tests; TypeScript/Vite build and ESLint scripts.

## Roles and access

| Role | Access |
|---|---|
| `EMPLOYEE` | Create incidents; read/comment on own reported incidents; see own notifications |
| `TECHNICIAN` | Shared incident queue; assignment/state changes; comments; workload; own profile |
| `ADMIN` | Staff access plus user/role, technician skill, strategy and reference views |

Registration creates an employee account. Admin provisioning uses configured bootstrap and user administration. API JSON can serialize the employee role as `USER`; persisted enum is `EMPLOYEE`.

## Workflow

The model includes `REPORTED → CLASSIFIED → PRIORITIZED → ASSIGNED → INVESTIGATING → RESOLVED → VERIFIED → CLOSED`. Creation advances through classification and priority. Assignment needs an eligible technician; resolution requires resolution details. Assignment reset to prioritized and reopening resolved to investigation are supported. Closed incidents are terminal. See lifecycle classes for exact guards.

## Specification alignment and gaps

The core ticket lifecycle, roles, audit, strategies, SLA monitoring and notifications are present. Optional categorization assistance, richer collaboration, feedback, downloadable reports and intelligent analysis remain future requirements. Reference values are seeded and shown in Admin screens; CRUD is not exposed. The assignment demo recording is not present.
