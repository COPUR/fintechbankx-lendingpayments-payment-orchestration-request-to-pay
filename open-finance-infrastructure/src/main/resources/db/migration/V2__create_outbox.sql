-- Transactional outbox for the evt.pay.rtp namespace. Rows are written in the
-- same transaction as the pay request and relayed to Kafka by OutboxRelay.

CREATE TABLE outbox_event (
    event_id          UUID          PRIMARY KEY,
    created_seq       BIGINT        GENERATED ALWAYS AS IDENTITY,
    aggregate_type    VARCHAR(64)   NOT NULL,
    aggregate_id      VARCHAR(64)   NOT NULL,
    aggregate_version BIGINT        NOT NULL,
    event_type        VARCHAR(128)  NOT NULL,
    topic             VARCHAR(249)  NOT NULL,
    payload           JSONB         NOT NULL,
    correlation_id    VARCHAR(128)  NOT NULL,
    traceparent       VARCHAR(55),
    occurred_at       TIMESTAMPTZ   NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    status            VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    published_at      TIMESTAMPTZ,
    attempts          INTEGER       NOT NULL DEFAULT 0,
    last_error        VARCHAR(512),

    CONSTRAINT uq_outbox_created_seq UNIQUE (created_seq),
    CONSTRAINT ck_outbox_topic_namespace CHECK (topic LIKE 'evt.pay.rtp.%'),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'PARKED')),
    CONSTRAINT ck_outbox_published CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL))
);

-- The relay reads pending rows in insertion order.
CREATE INDEX ix_outbox_pending ON outbox_event (created_seq) WHERE status = 'PENDING';
CREATE INDEX ix_outbox_published_at ON outbox_event (published_at) WHERE status = 'PUBLISHED';
CREATE INDEX ix_outbox_parked ON outbox_event (created_seq) WHERE status = 'PARKED';

COMMENT ON TABLE outbox_event IS 'Pending, parked and recently published pay request events; published rows purged after requesttopay.outbox.retention.';
