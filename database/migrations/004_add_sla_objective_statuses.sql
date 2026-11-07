-- Add independent response and resolution SLA states while preserving the
-- existing sla_status compatibility column and values.
BEGIN;

ALTER TABLE incidents
    ADD COLUMN response_sla_status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN resolution_sla_status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED';

-- Preserve the meanings that can be carried forward from the legacy summary.
UPDATE incidents
SET response_sla_status = CASE sla_status
        WHEN 'RESPONSE_BREACHED' THEN 'BREACHED'
        WHEN 'WITHIN_SLA' THEN 'WITHIN_SLA'
        WHEN 'MET' THEN 'MET'
        ELSE 'NOT_STARTED'
    END,
    resolution_sla_status = CASE sla_status
        WHEN 'RESOLUTION_BREACHED' THEN 'BREACHED'
        WHEN 'WITHIN_SLA' THEN 'WITHIN_SLA'
        WHEN 'MET' THEN 'MET'
        ELSE 'NOT_STARTED'
    END;

ALTER TABLE incidents
    ADD CONSTRAINT chk_incidents_response_sla_status
        CHECK (response_sla_status IN (
            'NOT_STARTED', 'WITHIN_SLA', 'APPROACHING_BREACH', 'BREACHED', 'MET'
        )),
    ADD CONSTRAINT chk_incidents_resolution_sla_status
        CHECK (resolution_sla_status IN (
            'NOT_STARTED', 'WITHIN_SLA', 'APPROACHING_BREACH', 'BREACHED', 'MET'
        ));

-- The priority-based policy selector requires at most one policy per priority.
-- Existing policy data has one row for each P1-P4 priority.
ALTER TABLE sla_policies
    ADD CONSTRAINT uq_sla_policies_priority UNIQUE (priority);

COMMIT;
