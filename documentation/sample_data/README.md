# Sample and reference data

The canonical records are seeded by [`database/seed/001_reference_data.sql`](../../database/seed/001_reference_data.sql) and [`002_skills.sql`](../../database/seed/002_skills.sql). Run the read-only validation script at [`database/verify_reference_data.sql`](../../database/verify_reference_data.sql) after loading them. The Admin lookup view and incident form consume API/database data; these examples are not frontend-only constants.

## Required category examples

Hardware, Software, Network, Database, Cloud, Security, Authentication, Application, Storage. The database seed also retains Access and Accounts.

## Required service examples

Cloud Applications, Database Services, Network Services, Authentication Services, Enterprise Applications, Storage Services, Communication Platforms, Digital Services, Payment Application, Cloud Storage. Other existing seeded services are retained.

## SLA sample values

| Priority | Response | Resolution |
|---|---:|---:|
| P1 | 5 min | 30 min |
| P2 | 15 min | 2 hr |
| P3 | 1 hr | 8 hr |
| P4 | 4 hr | 24 hr |

Values originate from `001_reference_data.sql`; conflicts are ignored so a repeat seed does not overwrite existing reference rows or policy values.
