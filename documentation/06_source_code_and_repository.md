# 6. Source code and repository

## Repository information

- Remote configured in this checkout: [github.com/ShreemayeeGuriya/IT-incident-management](https://github.com/ShreemayeeGuriya/IT-incident-management).
- Current local branch at inspection: `feature/frontend-v2` (may change).
- License, contribution policy, pull request requirements and CI workflow are not specified by the checked-in files inspected here.

## Layout

- `backend/src/main/java/com/incident/backend/assignment`: assignment rules and strategies.
- `config`: startup and scheduling configuration; `controller`: REST API.
- `dto`: transport objects; `events`: domain events/listeners.
- `lifecycle`: state model; `model`: entities/enums; `priority`: priority strategies.
- `repository`: JPA repositories; `security`: JWT/filter/config; `service`: use cases.
- `backend/src/test/java`: unit, API and opt-in PostgreSQL integration tests.
- `database/schema`, `migrations`, `seed`: baseline, upgrade path and reference data.
- `frontend/src/api`, `auth`, `components`, `pages`, `styles`, `types`: client layers.
- `documentation`: project documentation and diagram sources/previews.

## Local run and checks

Install JDK 17+, Node compatible with Vite 8, and PostgreSQL. Follow root [README](../README.md) and [database setup](../database/README.md). From `backend/`, run `./mvnw.cmd spring-boot:run` on Windows (API port 8080 default); in `frontend/`, run `npm install` and `npm run dev`.

- Backend unit/API: `./mvnw test` from backend.
- PostgreSQL IT: explicitly enable/select `Postgres*IT` with an isolated current-schema database.
- Frontend: `npm run build`, `npm run lint`.

Actual results for the documentation run are recorded in [testing](08_testing_documentation.md).

## API surface summary

Routes are under `/api`; except login/register/preflight, JWT is required and role-specific guards apply.

| Area | Routes |
|---|---|
| Auth | `POST /auth/register`, `/auth/login`; `GET /auth/me` |
| Incidents | create, duplicate suggestions, list, detail/update/delete |
| Incident actions | assign, status/state, comments, audit history |
| Lookups | `GET /categories`, `/services`, `/sla-policies` |
| Assignment | recommendation/auto-assign, strategy selection, workload, skills |
| Priority | preview, assessment/recalculate, strategy selection |
| Users | list/detail/assignees, role update, delete |
| Notifications | list, unread count, mark read |

See [`../docs/API_REFERENCE.md`](../docs/API_REFERENCE.md) and controller annotations for details. Category/service write APIs are absent.
