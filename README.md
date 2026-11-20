# Radar Incident Management System

Radar is a web based IT incident management application for reporting, triaging, assigning, and resolving operational incidents. It combines a React frontend with a Spring Boot REST API and PostgreSQL persistence. Employees report and track incidents, technicians work assigned incidents, and administrators manage users and operational decision settings.

## Contents

- [Problem and scope](#problem-and-scope)
- [Features and roles](#features-and-roles)
- [Decision support](#decision-support)
- [Architecture and technology](#architecture-and-technology)
- [Repository layout](#repository-layout)
- [Data model and persistence](#data-model-and-persistence)
- [Setup](#setup)
- [API overview](#api-overview)
- [Workflows](#workflows)
- [Object-oriented design](#object-oriented-design)
- [Testing](#testing)
- [Screenshots and demo](#screenshots-and-demo)
- [Limitations and future work](#limitations-and-future-work)
- [Contributors and license](#contributors-and-license)

## Problem and scope

IT support teams need a consistent way to capture service disruptions, prioritize work, route incidents to available technicians, and track resolution against response and resolution targets. Radar provides those workflows with role-based access, persistent incident history, and operational dashboards. The application currently implements the features described below; it is not an external email or paging service.

## Features and roles

| Role | Capabilities |
|---|---|
| Employee (`EMPLOYEE`, exposed in API user data as `USER`) | Register and sign in, report incidents, view and update permitted fields on their own incidents, add comments, review status/history, and view notifications. |
| Technician | View the shared incident queue and assigned work, inspect incident history and SLA state, update workflow status, add resolution details and comments, and view workload. |
| Administrator | View/manage users and roles, maintain technician skills, choose priority and assignment strategies, and view reference categories, services, and SLA policies. Category/service and SLA policy pages are read-only in the current API. |

Other implemented capabilities include incident search, filtering, sorting and pagination; incident audit history; in-app notifications; duplicate/similar incident suggestions; SLA monitoring and escalation; and dashboards/reports. Dashboard and report summaries are based on the first 100 incidents returned by the API, rather than an unbounded aggregate query.

### Decision support

**Priority calculation.** The default `WEIGHTED_SCORE` strategy maps impact and urgency to LOW=1, MEDIUM=2, HIGH=3, multiplies the values, then assigns P1 for scores 18 or higher, P2 for 9–17, P3 for 4–8, and P4 below 4. The alternate `IMPACT_URGENCY_MATRIX` strategy uses a configured matrix and critical-impact adjustment. Administrators can switch the active strategy at runtime; this choice is held in process memory and returns to the configured default after restart. Priority is not recalculated after an incident reaches resolved/verified/closed states.

**Technician assignment.** Eligible technicians must be active, available, and below their maximum workload. The default `SKILL_BASED` strategy prefers technicians whose persisted skills match the incident category, then chooses the least-loaded match. `LOAD_BASED` chooses by active-workload ratio, with deterministic tie breaks. `ROUND_ROBIN` cycles eligible technicians in ID order. If skill-based assignment finds no matching eligible technician, it reports that no candidate is available rather than silently assigning an unqualified technician. The round-robin cursor is process-local and resets after restart.

**Similar incident suggestions.** The duplicate-suggestion endpoint compares normalized issue concepts using a weighted token/Dice-style score and synonym/concept normalization, with category and service as supporting signals. It excludes the incident being checked and ranks suggestions. This is a heuristic, not a machine-learning model or embedding-based semantic search; results should be treated as suggestions.

## Architecture and technology

```text
Browser (React / TypeScript)
          | HTTP JSON + JWT
          v
Spring Boot REST API (validation, security, services)
          | Spring Data JPA
          v
PostgreSQL
```

| Area | Technologies verified in the repository |
|---|---|
| Frontend | React 19, TypeScript, React Router 7, Vite, npm, ESLint |
| Backend | Java 17, Spring Boot 3.5, Spring Web, Spring Security, Spring Data JPA, Bean Validation |
| Persistence | PostgreSQL; H2 is included for tests |
| Authentication | JWT (JJWT 0.12.6), BCrypt password hashing |
| Build/test | Maven wrapper, Spring Boot Test, Spring Security Test, TypeScript compiler, Vite build |

## Repository layout

```text
backend/                 Spring Boot application, configuration, and tests
  src/main/java/         API, domain, persistence, and application services
  src/main/resources/    application configuration
  src/test/java/         unit and API tests, plus opt-in PostgreSQL tests
database/
  schema/schema.sql      baseline schema
  migrations/            ordered SQL migrations
  seed/                  reference categories, services, skills, and SLA policies
  verify_schema.sql      schema verification SQL
  verify_reference_data.sql  reference-data verification SQL
  README.md              database-specific notes
docs/                    API and project documentation
documentation/           project documentation artifacts
frontend/
  src/                   React application, pages, components, API client
  .env.example           frontend API URL example
  package.json           frontend scripts and dependencies
.env.example             backend/database configuration example
```

## Data model and persistence

PostgreSQL stores application data; JPA validates the schema at application startup rather than generating it. The principal tables are:

| Table | Purpose and relationships |
|---|---|
| `users` | Accounts, hashed passwords, roles, and account metadata. |
| `technicians` | Technician profile, availability, workload capacity; one-to-one with a user. |
| `skills`, `technician_skills` | Skill reference values and the many-to-many technician skill assignments. Category names are seeded as skills. |
| `categories`, `services` | Incident classification and service reference values. Incidents reference each independently; the schema does not define a category-to-service mapping. |
| `sla_policies` | Response and resolution targets keyed by priority. |
| `incidents` | Reporter, optional technician, category, service, priority inputs/result, status, timestamps, SLA deadlines and objective status. |
| `comments` | Incident discussion, linked to incident and author. |
| `audit_logs` | Persistent incident change/history events. |
| `notifications` | Persistent in-app notifications and simulated email delivery tracking. |
| `incident_escalations` | Idempotent escalation records for response and resolution SLA levels. |

Foreign keys and uniqueness constraints protect core relationships. The seed scripts use conflict-safe inserts so reference values can be reapplied. The supplied reference data includes the nine requested incident categories plus additional existing categories, and service examples including Cloud Applications, Database Services, Network Services, Authentication Services, Enterprise Applications, Storage Services, Communication Platforms, Digital Services, Payment Application, and Cloud Storage.

### Database initialization note

There is a mismatch between the checked-in baseline schema and the migration sequence: `database/schema/schema.sql` already creates `incident_escalations`, while it does not contain all structures added by migrations 002–004 (comments, notification delivery fields, and separate SLA objective status fields). The steps below apply migrations 001–004 to a fresh baseline and intentionally omit migration 005, which would try to create the escalation table again. For an existing legacy database that has not received migrations, apply migrations 001–005 in order. Do not apply the same migration twice to a database where its table/columns already exist. The database-specific README describes a simpler baseline flow that does not account for this difference; use the actual schema/migration files and verify scripts as the source of truth.

## Setup

### Prerequisites

- Java 17
- Node.js and npm
- PostgreSQL

Create a PostgreSQL database, then from the repository root apply the baseline, the required fresh-install migrations, and reference seeds. Example for a local PostgreSQL account named `postgres` and database `radar_itsm`:

```powershell
createdb -U postgres radar_itsm
psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/schema/schema.sql
psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/migrations/001_add_users_created_at.sql
psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/migrations/002_create_comments.sql
psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/migrations/003_add_notification_delivery_tracking.sql
psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/migrations/004_add_sla_objective_statuses.sql
psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/seed/001_reference_data.sql
psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/seed/002_skills.sql
psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/verify_schema.sql
psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/verify_reference_data.sql
```

The application also runs the reference seed scripts during PostgreSQL startup. Database schema/migration setup must be completed before startup because Hibernate is configured to validate the schema.

### Environment configuration

Copy the root `.env.example` to `.env` and replace database/JWT values. The backend imports the root `.env` when launched from the `backend` directory. Keep secrets local; `.env` is excluded from version control.

| Variable | Purpose |
|---|---|
| `DB_URL` | JDBC URL for PostgreSQL. Example file uses a placeholder database name. |
| `DB_USERNAME`, `DB_PASSWORD` | Database login. |
| `JWT_SECRET` | Signing secret; must be at least 32 characters. |
| `APP_ADMIN_NAME`, `APP_ADMIN_EMAIL`, `APP_ADMIN_PASSWORD` | Optional bootstrap administrator settings. A blank admin password skips bootstrap; configured defaults are present in application properties. |
| `NOTIFICATION_EMAIL_SIMULATE_FAILURE_FOR` | Optional recipient address for simulating email delivery failure. Email is simulated/logged, not sent through SMTP. |
| `NOTIFICATION_EMAIL_RETRY_INTERVAL_MS`, `NOTIFICATION_EMAIL_RETRY_INITIAL_DELAY_MS` | Simulated email retry schedule. |
| `SLA_APPROACHING_THRESHOLD` | Fraction of an SLA target at which it becomes approaching breach; default `0.8`. |
| `SLA_MONITOR_INTERVAL_MS`, `SLA_MONITOR_INITIAL_DELAY_MS` | SLA monitor schedule. |
| `SLA_MONITOR_BATCH_SIZE` | Maximum policies/incidents processed in a monitor batch. |

The frontend has a separate optional `frontend/.env` setting. `VITE_API_URL` sets the API base URL and defaults to `http://localhost:8080/api` in the frontend client. The checked-in `frontend/.env.example` is the reference for its example value.

### Run the backend

From `backend`:

```powershell
.\mvnw.cmd spring-boot:run
```

The API listens on port `8080` by default. The configured CORS origins include `http://localhost:3000` and `http://localhost:5173`.

### Run the frontend

From `frontend`:

```powershell
npm ci
npm run dev
```

Vite prints the local development URL when it starts. For a production build use `npm run build`; preview a completed build with `npm run preview`.

## API overview

All routes below are under `/api`. Except registration/login and CORS preflight, requests require a bearer JWT. Role restrictions apply as described; exact request/response fields are documented in [`docs/API_REFERENCE.md`](docs/API_REFERENCE.md).

| Group | Implemented routes | Purpose / access |
|---|---|---|
| Authentication | `POST /auth/register`, `POST /auth/login`, `GET /auth/me` | Register employee, authenticate, inspect current account. |
| Incidents | `POST /incidents`, `GET /incidents`, `GET/PUT/DELETE /incidents/{id}` | Create and manage incidents; list supports search, filters, sorting, and pagination. Employees are restricted to their own records for applicable operations. |
| Similar suggestions | `POST /incidents/duplicate-suggestions` | Find ranked possible duplicates from submitted incident information. |
| Incident workflow | `PATCH /incidents/{id}/assign`, `PATCH /incidents/{id}/status`, `PATCH /incidents/{id}/state` | Assign and transition an incident. |
| Comments/history | `GET/POST /incidents/{id}/comments`, `GET /incidents/{id}/audit` | Read/add comments and read incident audit history. |
| Reference data | `GET /categories`, `GET /services`, `GET /sla-policies`, `GET /skills` | Read categories, services, SLA policies, and skills. |
| Users | `GET /users`, `GET /users/assignees`, `GET /users/{id}`, `PUT /users/{id}/role`, `DELETE /users/{id}` | Administrator user management; assignee listing is available to staff. |
| Technician skills/workload | `GET/PUT /technicians/{id}/skills`, `GET /technicians/workload` | Read/update skills (write is Admin-only) and view staff workload. |
| Assignment decisions | `POST /incidents/{id}/auto-assign`, `GET /incidents/{id}/assignment/recommendation`, `GET /assignment/strategies`, `PUT /assignment/strategy` | Recommend/assign technician and inspect or change the Admin-controlled strategy. |
| Priority decisions | `POST /priority/preview`, `GET /incidents/{id}/priority/assessment`, `POST /incidents/{id}/priority/recalculate`, `GET /priority/strategies`, `PUT /priority/strategy` | Preview, assess, recalculate priority, and manage the Admin-controlled strategy. |
| Notifications | `GET /notifications`, `GET /notifications/unread-count`, `PATCH /notifications/{id}/read` | Read notifications and mark one as read. |

The category, service, SLA policy, and skill APIs are read-only; there are no category/service CRUD endpoints in the current implementation.

## Workflows

### Incident lifecycle

New incidents are classified and prioritized during creation, then can be assigned and worked through `REPORTED → CLASSIFIED → PRIORITIZED → ASSIGNED → INVESTIGATING → RESOLVED → VERIFIED → CLOSED`. Assignment requires an eligible technician. Resolution requires resolution details. Staff can reopen a resolved incident for investigation; an assigned incident can return to prioritized/unassigned work. Closed incidents are terminal. Employees can only change fields allowed by ownership and the employee update rules.

### SLA, escalation, audit, and notifications

Response and resolution deadlines are derived from the incident priority's SLA policy. The response clock completes on the first response (entering investigation) or resolution/closure; the resolution objective completes on resolution/closure. The scheduled monitor updates objective states (including approaching breach using the configured threshold) and creates response/resolution escalation records and notifications. Escalations are unique per incident and level. Incident changes are recorded in `audit_logs`; comments remain a separate conversation stream. In-app notifications persist in the database. Email delivery is currently simulated with logging and scheduled retries, not an external mail provider.

## Object-oriented design

The backend separates REST controllers, application services, domain entities, repositories, and request/response DTOs. Spring Data repositories encapsulate database access, and dependency injection supplies service collaborators. Strategy interfaces and implementations encapsulate priority and technician assignment policies. Incident state transitions are validated by lifecycle/domain logic, and Spring application events connect incident changes to audit and notification listeners. These are implementation patterns visible in the code, not claims of a formal pattern framework.

## Testing

Backend unit and API tests are under `backend/src/test/java`. Run the standard suite from `backend`:

```powershell
.\mvnw.cmd test
```

PostgreSQL integration tests are opt-in and are protected by a database-name guard that permits only the dedicated `IT_incident_management` database. Configure the PostgreSQL test properties as documented in the backend test resources, then select the integration tests explicitly, for example:

```powershell
.\mvnw.cmd "-Dpostgres.integration=true" "-Dtest=Postgres*IT" test
```

Frontend checks are available from `frontend`:

```powershell
npm run lint
npm run build
```

The repository does not include a browser end-to-end test runner or a published coverage report.

## Screenshots and demo

No application screenshots or demo recording are included in the repository at this time.

## Limitations and future work

Current limitations include simulated rather than provider-backed email, read-only category/service/SLA reference APIs, process-local runtime strategy selection and round-robin cursor, heuristic similarity suggestions, and dashboard/report summaries limited to the first 100 fetched incidents. The baseline schema and migration sequence also need reconciliation, as noted under [Database initialization](#database-initialization-note).

Possible future improvements include reference-data administration APIs, external email integration, persisted strategy configuration, richer semantic duplicate search, aggregate reporting over all records, browser end-to-end tests, and a single documented schema initialization path. These are suggestions only and are not represented as implemented features.


## Related project documentation

- [API reference](docs/API_REFERENCE.md)
- [Database notes](database/README.md)
- [Frontend README](frontend/README.md)
