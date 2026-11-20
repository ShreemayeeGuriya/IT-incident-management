# 5. Technology stack

Versions are read from project manifests/configuration on 20 November 2026. Direct versions/ranges are shown; resolved transitive versions may differ by lockfile.

| Area | Technology | Use |
|---|---|---|
| Language | Java 17 | Backend/domain |
| Backend | Spring Boot 3.5.6 | Application and REST |
| Web/security | Spring Web, Spring Security | HTTP API, stateless JWT security |
| Persistence | Spring Data JPA, JDBC, PostgreSQL driver | ORM and specialized technician queries |
| Validation | Jakarta Bean Validation | Request DTO validation |
| Authentication | JJWT 0.12.6, BCrypt | Signed token and password hashing |
| Frontend | React 19.3.1, TypeScript ~6.0.3 | SPA and typed UI |
| Routing/build | React Router 7.18.6, Vite 8.x | Navigation and build |
| Frontend quality | ESLint 10.x, TypeScript ESLint | Static checks |
| Database | PostgreSQL 15+ per README | Durable system of record |
| Tests | Spring Boot Test, Spring Security Test, H2 | Unit/API and isolated tests; PostgreSQL ITs opt-in |
| Build | Maven wrapper, npm | Backend/frontend workflows |

## Configuration

Backend requires `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and a sufficiently long `JWT_SECRET`. Optional variables configure bootstrap admin, simulated mail and SLA monitoring. `.env` is local/ignored. Frontend API URL is configured through Vite env settings; see `frontend/.env.example`.

## OOP concepts and patterns

- **Strategy:** priority and technician assignment interfaces have alternative implementations selected through a registry.
- **State:** `IncidentState` implementations validate transitions and state-specific guards.
- **Observer/event listener:** application events trigger audit, notification and simulated-email reactions.
- **Repository:** Spring Data and JDBC repositories isolate persistence.
- Encapsulation, polymorphism and interfaces are applied across entities/services and strategy/state types.

The application does not include external ML, cloud hosting, or a live email provider.
