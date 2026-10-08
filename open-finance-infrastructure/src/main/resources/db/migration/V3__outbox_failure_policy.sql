-- Outbox failure policy (ADR-021 decision 4). The relay parks a row only for a
-- payload error that can never succeed (RecordTooLarge, Serialization,
-- InvalidTopic). Every other error stops the batch without marking any row and
-- is retried with backoff; such a row is never parked automatically. Only an
-- operator parks it by hand, recording why in park_reason (runbook).
-- A parked row keeps its aggregate's later rows pending (per-aggregate order).

ALTER TABLE outbox_event ADD COLUMN parked_at TIMESTAMPTZ;
ALTER TABLE outbox_event ADD COLUMN park_reason VARCHAR(256);

ALTER TABLE outbox_event ADD CONSTRAINT ck_outbox_parked
    CHECK ((status = 'PARKED') = (parked_at IS NOT NULL AND park_reason IS NOT NULL));

-- The relay skips aggregates that have a parked row.
CREATE INDEX ix_outbox_parked_aggregate ON outbox_event (aggregate_id, created_seq) WHERE status = 'PARKED';

COMMENT ON COLUMN outbox_event.parked_at IS 'When the row was parked; NULL again after a manual replay (status back to PENDING).';
COMMENT ON COLUMN outbox_event.park_reason IS 'Why the row was parked: "payload error (relay)" or the operator''s recorded reason (ticket and cause).';
