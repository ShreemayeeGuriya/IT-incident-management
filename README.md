# Radar ITSM

Radar is a Spring Boot 3 / Java 17 REST backend backed by PostgreSQL. It provides user authentication, incident persistence, audit history, in-app notifications, simulated email delivery, and SLA monitoring.

## Prerequisites

- JDK 17 or later.
- PostgreSQL 15 or compatible, plus `psql` for database setup.
- The Maven wrapper included in `backend/` (no separately installed Maven is required).

## Configure the environment

Copy `.env.example` to `.env` in the repository root, then replace the placeholders. `.env` is ignored by Git; do not commit it. The backend reads variables from the operating-system environment and imports property files using `spring.config.import` in `backend/src/main/resources/application.properties`:

```properties
optional:file:../.env[.properties],optional:file:.env[.properties]
```

Run the backend with `backend/` as the working directory. From there, `../.env` resolves to the repository-root `.env`, and `.env` resolves to an optional `backend/.env`. Spring Boot does not load a root `.env` by convention; this project loads it because of that explicit import. OS environment variables can also provide the same settings.

Required connection settings are `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. `JWT_SECRET` is required for every application run; startup fails if it is absent or too short. Generate a random local value and replace the placeholder in `.env` (test contexts use an isolated test-only key). Never reuse a test or example value outside tests. Set `APP_ADMIN_PASSWORD` if startup should create the first admin when none exists. The other optional notification/SLA variables and defaults are listed in `.env.example` and `backend/src/main/resources/application.properties`.

## Database setup

Follow [database/README.md](database/README.md) for the distinct fresh-database and existing-database procedures. For a new database, apply `database/schema/schema.sql` once, then `database/seed/001_reference_data.sql`. The baseline includes the current migration structures through 006; do not apply those migrations after the baseline.

## Run the backend

From PowerShell:

```powershell
Copy-Item .env.example .env
Set-Location backend
./mvnw.cmd spring-boot:run
```

From macOS/Linux:

```sh
cp .env.example .env
cd backend
./mvnw spring-boot:run
```

Edit `.env` before startup so all placeholder values are replaced. The API listens on port 8080 by default.

## Run tests

From `backend/`, unit/API tests can be run with:

```sh
./mvnw test
```

PostgreSQL integration tests are opt-in and must be selected explicitly because their class names end in `IT`. Ensure the configured test database has the current schema and reference data, then run:

```sh
./mvnw '-Dpostgres.integration=true' '-Dtest=AuthApiTest,SlaServiceTest,Postgres*IT' test
```

The PostgreSQL integration tests use the configured database. Do not point them at a production database.
