-- One Kafka topic per aggregate (ADR-019 sections 1, 3 and 8; owner decision
-- 2026-10-08): every event of the PayRequest aggregate goes to evt.pay.rtp.v1,
-- named by its eventType record header. The relay computes the topic and no
-- longer reads outbox_event.topic; rows written before this migration stored
-- the old per-event topic, so they are pointed at the aggregate topic here and
-- the operator queries of the runbook show where a row goes. Nothing was ever
-- relayed to the per-event topics (the relay has been off everywhere).
--
-- DML on an existing table; V5's table grant covers the runtime role.

UPDATE outbox_event SET topic = 'evt.pay.rtp.v1' WHERE topic <> 'evt.pay.rtp.v1';

COMMENT ON COLUMN outbox_event.topic IS
    'Aggregate topic evt.pay.rtp.v1 (V7, one topic per aggregate); the relay computes it and does not read this column.';
