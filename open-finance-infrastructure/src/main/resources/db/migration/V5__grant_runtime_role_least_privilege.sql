-- Flyway runs as the schema owner (DB_MIGRATION_USERNAME), which owns
-- sc_pay_request_to_pay and every table in it. The service connects as the
-- runtime role (${runtime_role}, from DB_USERNAME) and gets only the DML the
-- code issues:
--   pay_request              SELECT, INSERT, UPDATE           (aggregate; never deleted)
--   pay_request_idempotency  SELECT, INSERT, DELETE           (keys are inserted, read, and purged when expired)
--   dpop_proof_jti           SELECT, INSERT, DELETE           (replay cache; purge of expired jti)
--   outbox_event             SELECT, INSERT, UPDATE, DELETE   (relay marks, parks and purges rows)
-- plus USAGE on the schema (created_seq is an identity column, so no sequence
-- grant). Not being the owner, it can neither CREATE, ALTER, DROP nor
-- TRUNCATE, and it cannot read flyway_schema_history.
-- Every later migration that adds a table grants the runtime role explicitly.
--
-- Local single-user runs (no DB_MIGRATION_USERNAME) migrate as the runtime
-- role itself; then there is nothing to separate and this migration only says
-- so, because revoking the owner's own privileges would break later migrations.

DO $$
DECLARE
    runtime_role text := '${runtime_role}';
BEGIN
    IF runtime_role = current_user THEN
        RAISE NOTICE 'runtime role % is the schema owner (single-user run): privileges not separated', runtime_role;
        RETURN;
    END IF;

    EXECUTE format('REVOKE ALL ON SCHEMA %I FROM %I', current_schema(), runtime_role);
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO %I', current_schema(), runtime_role);
    EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA %I FROM %I', current_schema(), runtime_role);
    EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA %I FROM PUBLIC', current_schema());

    EXECUTE format('GRANT SELECT, INSERT, UPDATE ON TABLE pay_request TO %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT, DELETE ON TABLE pay_request_idempotency TO %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT, DELETE ON TABLE dpop_proof_jti TO %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE outbox_event TO %I', runtime_role);
END
$$;
