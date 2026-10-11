-- terraform-modules aurora-postgresql role_bootstrap_sql (1e6ca85): the
-- runtime role gets DML on tables plus USAGE, SELECT on the schema's
-- sequences. The DBA bootstrap's ALTER DEFAULT PRIVILEGES covers sequences
-- the migration role creates after it ran; this grants the ones that exist
-- (outbox_event.created_seq's identity sequence), so a schema bootstrapped
-- after V2 matches one bootstrapped before. No UPDATE (setval) and nothing on
-- tables: V5 stays the table grant list.
--
-- Local single-user runs (no DB_MIGRATION_USERNAME) skip it, like V5.

DO $$
DECLARE
    runtime_role text := '${runtime_role}';
BEGIN
    IF runtime_role = current_user THEN
        RAISE NOTICE 'runtime role % is the schema owner (single-user run): nothing to grant', runtime_role;
        RETURN;
    END IF;

    EXECUTE format('GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA %I TO %I', current_schema(), runtime_role);
END
$$;
