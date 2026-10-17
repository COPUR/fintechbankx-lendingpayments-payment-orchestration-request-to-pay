-- svc-pay-request-to-pay owns schema sc_pay_request_to_pay in db_pay_request_to_pay_<env>.
-- Flyway runs with default-schema sc_pay_request_to_pay, so names are unqualified.
-- No other service reads these tables; other contexts use the API or evt.pay.rtp.* events.

CREATE TABLE pay_request (
    consent_id    VARCHAR(64)    PRIMARY KEY,
    tpp_id        VARCHAR(128)   NOT NULL,
    debtor_id     VARCHAR(128)   NOT NULL,
    creditor_name VARCHAR(140)   NOT NULL,
    amount        NUMERIC(19, 4) NOT NULL,
    currency      VARCHAR(3)     NOT NULL,
    status        VARCHAR(32)    NOT NULL,
    requested_at  TIMESTAMPTZ    NOT NULL,
    updated_at    TIMESTAMPTZ    NOT NULL,
    payment_id    VARCHAR(64),
    version       BIGINT         NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT ck_pay_request_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_pay_request_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_pay_request_status CHECK (status IN ('AWAITING_AUTHORISATION', 'REJECTED', 'CONSUMED')),
    -- Only a consumed (accepted) request points at the payment it produced.
    CONSTRAINT ck_pay_request_payment_id CHECK ((status = 'CONSUMED') = (payment_id IS NOT NULL))
);

CREATE INDEX ix_pay_request_tpp_status ON pay_request (tpp_id, status);
CREATE INDEX ix_pay_request_debtor ON pay_request (debtor_id);

COMMENT ON TABLE pay_request IS 'PayRequest aggregate (request to pay). debtor_id is the pseudonymous PSU id.';
COMMENT ON COLUMN pay_request.version IS 'Aggregate version: 0 when created, +1 per decision; optimistic lock.';

-- x-idempotency-key per TPP. Inserted in the same transaction as the pay
-- request; the deferred foreign key is checked at commit.
CREATE TABLE pay_request_idempotency (
    tpp_id              VARCHAR(128) NOT NULL,
    idempotency_key     VARCHAR(128) NOT NULL,
    request_fingerprint CHAR(64)     NOT NULL,
    consent_id          VARCHAR(64)  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    expires_at          TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_pay_request_idempotency PRIMARY KEY (tpp_id, idempotency_key),
    CONSTRAINT fk_pay_request_idempotency_request FOREIGN KEY (consent_id)
        REFERENCES pay_request (consent_id) DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX ix_pay_request_idempotency_expires ON pay_request_idempotency (expires_at);

-- DPoP proof replay guard shared by all replicas (SHA-256 of the proof jti).
CREATE TABLE dpop_proof_jti (
    jti_hash   CHAR(64)    PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_dpop_proof_jti_expires ON dpop_proof_jti (expires_at);
