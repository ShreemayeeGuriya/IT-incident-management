-- Skills used by SKILL_BASED technician assignment.
-- One skill per incident category: a technician with the "Network" skill can be
-- auto-assigned Network incidents. Skill names must match category names (case-insensitive).
-- Apply after 001_reference_data.sql. Safe to rerun.
BEGIN;

INSERT INTO skills (skill_name)
SELECT category_name FROM categories
ON CONFLICT (skill_name) DO NOTHING;

COMMIT;
