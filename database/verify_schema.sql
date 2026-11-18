-- Read-only structural verification for the current public schema.
-- Run with: psql -v ON_ERROR_STOP=1 -f database/verify_schema.sql
BEGIN TRANSACTION READ ONLY;

DO $$
DECLARE
    missing_items TEXT;
    status_constraint TEXT;
    status_value TEXT;
BEGIN
    SELECT string_agg(e.table_name, ', ' ORDER BY e.table_name)
      INTO missing_items
      FROM (VALUES
          ('users'), ('technicians'), ('skills'), ('technician_skills'),
          ('categories'), ('services'), ('sla_policies'), ('incidents'), ('incident_escalations'),
          ('comments'), ('audit_logs'), ('notifications')
      ) AS e(table_name)
     WHERE to_regclass('public.' || quote_ident(e.table_name)) IS NULL;
    IF missing_items IS NOT NULL THEN
        RAISE EXCEPTION 'Missing tables: %', missing_items;
    END IF;

    SELECT string_agg(e.table_name || '.' || e.column_name, ', ' ORDER BY e.table_name, e.column_name)
      INTO missing_items
      FROM (VALUES
          ('users','user_id'), ('users','name'), ('users','email'), ('users','password_hash'), ('users','role'), ('users','created_at'),
          ('technicians','technician_id'), ('technicians','user_id'), ('technicians','max_workload'), ('technicians','availability_status'), ('technicians','experience_level'),
          ('skills','skill_id'), ('skills','skill_name'),
          ('technician_skills','technician_id'), ('technician_skills','skill_id'), ('technician_skills','proficiency_level'),
          ('categories','category_id'), ('categories','category_name'), ('categories','description'),
          ('services','service_id'), ('services','service_name'), ('services','description'),
          ('sla_policies','sla_policy_id'), ('sla_policies','policy_name'), ('sla_policies','priority'), ('sla_policies','response_time_minutes'), ('sla_policies','resolution_time_minutes'),
          ('incidents','incident_id'), ('incidents','reporter_id'), ('incidents','assigned_technician_id'), ('incidents','category_id'), ('incidents','service_id'), ('incidents','sla_policy_id'),
          ('incidents','description'), ('incidents','impact'), ('incidents','urgency'), ('incidents','business_criticality'), ('incidents','priority'), ('incidents','status'),
          ('incidents','sla_status'), ('incidents','response_sla_status'), ('incidents','resolution_sla_status'), ('incidents','investigation_details'),
          ('incidents','resolution_details'), ('incidents','closure_details'), ('incidents','created_at'), ('incidents','first_response_at'), ('incidents','resolved_at'),
          ('incidents','closed_at'), ('incidents','response_deadline'), ('incidents','resolution_deadline'),
          ('incident_escalations','escalation_id'), ('incident_escalations','incident_id'),
          ('incident_escalations','escalation_level'), ('incident_escalations','trigger_objective'),
          ('incident_escalations','created_at'),
          ('comments','comment_id'), ('comments','incident_id'), ('comments','author_id'), ('comments','text'), ('comments','created_at'),
          ('audit_logs','audit_id'), ('audit_logs','incident_id'), ('audit_logs','actor_user_id'), ('audit_logs','action'), ('audit_logs','old_value'),
          ('audit_logs','new_value'), ('audit_logs','description'), ('audit_logs','timestamp'),
          ('notifications','notification_id'), ('notifications','user_id'), ('notifications','incident_id'), ('notifications','message'),
          ('notifications','notification_type'), ('notifications','delivery_status'), ('notifications','is_read'), ('notifications','created_at'),
          ('notifications','read_at'), ('notifications','event_key'), ('notifications','delivery_attempts'), ('notifications','last_delivery_error')
      ) AS e(table_name, column_name)
     WHERE NOT EXISTS (
         SELECT 1 FROM information_schema.columns c
          WHERE c.table_schema = 'public' AND c.table_name = e.table_name AND c.column_name = e.column_name
     );
    IF missing_items IS NOT NULL THEN
        RAISE EXCEPTION 'Missing columns: %', missing_items;
    END IF;

    SELECT string_agg(e.table_name || '.' || e.constraint_name, ', ' ORDER BY e.table_name, e.constraint_name)
      INTO missing_items
      FROM (VALUES
          ('users','users_pkey','p'), ('users','users_email_key','u'), ('users','chk_users_role','c'),
          ('technicians','technicians_pkey','p'), ('technicians','technicians_user_id_key','u'),
          ('technicians','fk_technicians_user','f'), ('technicians','chk_technicians_max_workload','c'), ('technicians','chk_technicians_availability','c'),
          ('skills','skills_pkey','p'), ('skills','skills_skill_name_key','u'),
          ('technician_skills','pk_technician_skills','p'), ('technician_skills','fk_technician_skills_technician','f'), ('technician_skills','fk_technician_skills_skill','f'),
          ('categories','categories_pkey','p'), ('categories','categories_category_name_key','u'),
          ('services','services_pkey','p'), ('services','services_service_name_key','u'),
          ('sla_policies','sla_policies_pkey','p'), ('sla_policies','uq_sla_policies_priority','u'),
          ('sla_policies','chk_sla_policies_priority','c'), ('sla_policies','chk_sla_response_time','c'), ('sla_policies','chk_sla_resolution_time','c'),
          ('incidents','incidents_pkey','p'), ('incidents','fk_incidents_reporter','f'), ('incidents','fk_incidents_assigned_technician','f'),
          ('incidents','fk_incidents_category','f'), ('incidents','fk_incidents_service','f'), ('incidents','fk_incidents_sla_policy','f'),
          ('incidents','chk_incidents_impact','c'), ('incidents','chk_incidents_urgency','c'), ('incidents','chk_incidents_business_criticality','c'),
          ('incidents','chk_incidents_priority','c'), ('incidents','chk_incidents_status','c'), ('incidents','chk_incidents_sla_status','c'),
          ('incidents','chk_incidents_response_sla_status','c'), ('incidents','chk_incidents_resolution_sla_status','c'), ('incidents','chk_incidents_timestamps','c'),
          ('incident_escalations','incident_escalations_pkey','p'),
          ('incident_escalations','fk_incident_escalations_incident','f'),
          ('incident_escalations','uq_incident_escalations_incident_level','u'),
          ('incident_escalations','chk_incident_escalations_level_objective','c'),
          ('comments','comments_pkey','p'), ('comments','fk_comments_incident','f'), ('comments','fk_comments_author','f'),
          ('audit_logs','audit_logs_pkey','p'), ('audit_logs','fk_audit_logs_incident','f'), ('audit_logs','fk_audit_logs_actor','f'),
          ('notifications','notifications_pkey','p'), ('notifications','fk_notifications_user','f'), ('notifications','fk_notifications_incident','f'),
          ('notifications','chk_notifications_delivery_status','c'), ('notifications','chk_notifications_read_timestamp','c'),
          ('notifications','chk_notifications_delivery_attempts','c')
      ) AS e(table_name, constraint_name, constraint_type)
     WHERE NOT EXISTS (
         SELECT 1 FROM pg_constraint c
         JOIN pg_class r ON r.oid = c.conrelid
         JOIN pg_namespace n ON n.oid = r.relnamespace
          WHERE n.nspname = 'public' AND r.relname = e.table_name
            AND c.conname = e.constraint_name AND c.contype::text = e.constraint_type
     );
    IF missing_items IS NOT NULL THEN
        RAISE EXCEPTION 'Missing or wrong-type constraints: %', missing_items;
    END IF;

    SELECT string_agg(e.index_name, ', ' ORDER BY e.index_name)
      INTO missing_items
      FROM (VALUES
          ('idx_technicians_availability'), ('idx_technician_skills_skill'),
          ('idx_incidents_reporter'), ('idx_incidents_assigned_technician'), ('idx_incidents_category'),
          ('idx_incidents_service'), ('idx_incidents_sla_policy'), ('idx_incidents_created_at'),
          ('idx_incidents_status_priority'), ('idx_incidents_sla_status'), ('idx_incidents_response_deadline'),
          ('idx_incidents_resolution_deadline'), ('idx_incident_escalations_incident_created'),
          ('idx_audit_logs_incident_timestamp'), ('idx_audit_logs_actor_timestamp'),
          ('idx_notifications_user_created'), ('idx_notifications_unread'), ('idx_notifications_incident'),
          ('uq_notifications_event_key'), ('idx_comments_incident_created_at')
      ) AS e(index_name)
     WHERE to_regclass('public.' || quote_ident(e.index_name)) IS NULL;
    IF missing_items IS NOT NULL THEN
        RAISE EXCEPTION 'Missing indexes: %', missing_items;
    END IF;

    FOREACH status_constraint IN ARRAY ARRAY[
        'chk_incidents_response_sla_status', 'chk_incidents_resolution_sla_status'
    ] LOOP
        SELECT pg_get_constraintdef(c.oid) INTO missing_items
          FROM pg_constraint c
          JOIN pg_class r ON r.oid = c.conrelid
          JOIN pg_namespace n ON n.oid = r.relnamespace
         WHERE n.nspname = 'public' AND r.relname = 'incidents' AND c.conname = status_constraint;
        FOREACH status_value IN ARRAY ARRAY[
            'NOT_STARTED', 'WITHIN_SLA', 'APPROACHING_BREACH', 'BREACHED', 'MET'
        ] LOOP
            IF missing_items IS NULL OR position(status_value IN missing_items) = 0 THEN
                RAISE EXCEPTION 'Constraint % does not contain expected status value %', status_constraint, status_value;
            END IF;
        END LOOP;
    END LOOP;

    RAISE NOTICE 'Schema verification passed for public schema.';
END
$$;

COMMIT;
