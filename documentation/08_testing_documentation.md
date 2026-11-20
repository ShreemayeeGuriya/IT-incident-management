# 8. Testing documentation

## Test approach

Backend coverage includes unit/service tests, API tests and opt-in PostgreSQL integration tests. Frontend checks are TypeScript/Vite production build and ESLint; `package.json` does not define a frontend test or browser E2E runner. PostgreSQL IT classes end in `IT`, so default Maven `test` does not run them.

## Test cases

| ID | Area | Scenario | Expected outcome | Evidence/status |
|---|---|---|---|---|
| TC-01 | Auth | Register employee with valid data | Account created; password hashed | Auth API test source present |
| TC-02 | Auth | Login with valid credentials | JWT returned | Auth API test source present |
| TC-03 | Auth | Protected request with invalid token | Rejected | Security test coverage; suite status below |
| TC-04 | Priority | Weighted strategy factors | Threshold maps to expected priority | Priority tests present |
| TC-05 | Priority | Matrix strategy/criticality adjustment | Expected priority | Priority tests present |
| TC-06 | Priority | Recalculate after factor change | Priority and SLA updated | Incident service test present |
| TC-07 | Assignment | Skill strategy | Eligible matching technician preferred | Assignment tests present |
| TC-08 | Assignment | Load strategy/tie | Least-loaded eligible technician selected | Assignment tests present |
| TC-09 | Assignment | Round robin | Eligible technicians rotate | Assignment tests present |
| TC-10 | Assignment | Unavailable/over-capacity candidate | Candidate excluded | Rule tests present |
| TC-11 | Lifecycle | Create incident | Reported then classified/prioritized | Lifecycle/service tests present |
| TC-12 | Lifecycle | Resolve without details | Guard rejects transition | Lifecycle tests present |
| TC-13 | Lifecycle | Change closed incident | Terminal-state guard applied | Lifecycle tests present |
| TC-14 | SLA | Priority deadline derivation | Policy durations used | SLA service/Postgres IT source present |
| TC-15 | SLA | Deadline breach | Objective/escalation recorded | SLA/escalation tests present |
| TC-16 | Similarity | Near-identical descriptions | Ranked candidate returned | Matcher tests present |
| TC-17 | Similarity | Different wording, same Wi-Fi issue | Suggestion above relevance threshold | Regression test source present; suite status below |
| TC-18 | Similarity | Unrelated incident | Not a high-confidence suggestion | Matcher tests present |
| TC-19 | Similarity | Current incident exclusion | Never returned as itself | Matcher/service tests present |
| TC-20 | Notification | Create/status event | Persistent recipients; actor excluded | Notification/Postgres tests present |
| TC-21 | Email simulation | Delivery failure | Attempt logged and retry bounded | Email IT source present |
| TC-22 | Reference data | Idempotent seeds | Missing values inserted without duplicates | SQL/initializer present; DB execution not run |
| TC-23 | Frontend | TypeScript and production build | Bundle emitted successfully | Passed in this run |
| TC-24 | Frontend | ESLint | No lint errors | Not run in this documentation task |

“Test source present” is not equivalent to a passing test run.

## Verification run for documentation

| Check | Command | Result |
|---|---|---|
| Frontend build | `npm run build` in `frontend/` | **Passed** — TypeScript succeeded; Vite 8.3.8 transformed 49 modules and emitted production assets. |
| Backend unit/API | `./mvnw.cmd -DforkCount=0 test` in `backend/` | **Not completed** — the wrapper invocation stalled without returning output and had to be interrupted; no test result is claimed. |
| PostgreSQL integration | Explicit `-Dpostgres.integration=true` selection | Not run; needs isolated configured PostgreSQL test DB. |
| Frontend lint | `npm run lint` | Not run. |
| Browser E2E | No runner in package scripts | Not configured. |

## Reproduce

From `backend/`, run `./mvnw.cmd test`. For opt-in integration tests use `./mvnw.cmd '-Dpostgres.integration=true' '-Dtest=AuthApiTest,SlaServiceTest,Postgres*IT' test` with an isolated current-schema PostgreSQL database. From `frontend/`, run `npm run build` and `npm run lint`.

Integration tests need PostgreSQL. Manual browser verification, deployment testing, and the assignment demo video are outside these checks.
