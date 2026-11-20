# PostgreSQL setup
# PostgreSQL setup

This guide covers a **new empty database** and an **existing database that predates migrations 001–006**. Do not mix the two paths.

## Prerequisites

- PostgreSQL 15 or compatible, with `psql` available on `PATH`.
- Java 17 or later.
- The repository's Maven wrapper (`backend/mvnw` or `backend/mvnw.cmd`).
- A local `.env` file with the application settings described in the root [README](../README.md).

Commands below use `radar_itsm` as the example database name and are run from the repository root unless stated otherwise. PostgreSQL may prompt for the database owner's password.

## Fresh database

1. Create an empty database:

   ```sh
   createdb -U postgres radar_itsm
   ```

2. Apply the complete baseline:

   ```sh
   psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/schema/schema.sql
   ```

`schema.sql` includes the final structures represented by migrations 001–006, including comments, notification delivery tracking, independent SLA objective states, incident escalations, and incident information requests. **Do not run migrations 001–006 after applying this baseline.**


3. Add the reference records needed by incident creation:

   ```sh
   psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/seed/001_reference_data.sql
   psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/seed/002_skills.sql
   ```

   These seeds use conflict handling that preserves existing reference rows and SLA policy values. Review the rows afterward if you have customized reference data.

   For an already initialized database, rerun both idempotent seeds to add the full OOP project category, IT service, and technician skill examples. They insert only names that are missing. On each PostgreSQL backend startup, the application reapplies both seeds in order so missing categories, services, SLAs, and category-based technician skills are available automatically.

4. Configure `.env` at the repository root, following the safe placeholders in `.env.example`, then start the backend as documented in the root README.

## Upgrade an existing database

Use this path only for an existing database whose base schema predates these migrations and already has the prerequisite `users`, `incidents`, `notifications`, and `sla_policies` tables. Do not apply `schema.sql` to that database.

Apply the migrations exactly once and in numeric order. From the repository root:

```sh
psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/migrations/001_add_users_created_at.sql
psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/migrations/002_create_comments.sql
psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/migrations/003_add_notification_delivery_tracking.sql
psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/migrations/004_add_sla_objective_statuses.sql
psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/migrations/001_add_users_created_at.sql
psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/migrations/002_create_comments.sql
psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/migrations/003_add_notification_delivery_tracking.sql
psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/migrations/004_add_sla_objective_statuses.sql
psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/migrations/005_create_incident_escalations.sql
psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/migrations/006_create_incident_information_requests.sql
psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/seed/001_reference_data.sql
psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/seed/001_reference_data.sql
psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/seed/002_skills.sql
```

Migrations 002, 003, 004, and 005 have explicit transaction boundaries. Migration 001 is one atomic `ALTER TABLE` statement. `ON_ERROR_STOP=1` makes `psql` stop if a statement fails; an open migration transaction is rolled back when the connection closes. Migrations 002–005 are not rerunnable; check the current schema before retrying any migration that may have partially or fully run.

This repository does not use a migration runner or record migration versions in a database ledger. Numeric filenames define the order. The schema verifier checks the resulting schema shape, not the historical execution record.

## Verify the resulting schema

Run the verifier after either setup path:

```sh
psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/verify_schema.sql
```

It opens a read-only transaction and raises an error if expected tables, columns, indexes, foreign keys, primary/unique constraints, or check constraints are missing. It does not alter the database. Confirm the seed contents separately:

Run the reference-data check after applying the seeds:

```sh
psql -U postgres -d radar_itsm -v ON_ERROR_STOP=1 -f database/verify_reference_data.sql
```

This read-only check verifies all category and service examples required by the project brief.

```sql
SELECT category_name FROM categories ORDER BY category_name;
SELECT service_name FROM services ORDER BY service_name;
SELECT priority, policy_name, response_time_minutes, resolution_time_minutes
FROM sla_policies ORDER BY priority;
```

To create incidents, at least one category, service, and matching policy for each used priority must exist. A reporter must be registered (or an initial admin created with `APP_ADMIN_PASSWORD`).
