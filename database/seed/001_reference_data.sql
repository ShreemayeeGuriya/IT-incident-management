-- Minimal lookup and SLA data required to create incidents.
-- Safe to rerun: existing lookup rows and policy values are never overwritten.
BEGIN;

INSERT INTO categories (category_name, description)
VALUES
    ('Hardware', 'Physical devices and equipment'),
    ('Software', 'Applications and software issues'),
    ('Network', 'Connectivity and network access'),
    ('Access and Accounts', 'Identity, account, and permission issues')
ON CONFLICT (category_name) DO NOTHING;

INSERT INTO services (service_name, description)
VALUES
    ('End User Computing', 'Workstations, laptops, and peripherals'),
    ('Business Applications', 'Business software and application services'),
    ('Network Services', 'Corporate network and connectivity'),
    ('Identity and Access', 'Authentication and access management')
ON CONFLICT (service_name) DO NOTHING;

INSERT INTO sla_policies (policy_name, priority, response_time_minutes, resolution_time_minutes)
VALUES
    ('Critical', 'P1',   5,   30),
    ('High',     'P2',  15,  120),
    ('Medium',   'P3',  60,  480),
    ('Low',      'P4', 240, 1440)
ON CONFLICT (priority) DO NOTHING;

COMMIT;
