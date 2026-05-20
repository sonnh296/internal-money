-- PaymentOrchestrator: V1 - Consolidated Schema
CREATE TABLE IF NOT EXISTS payments (
    payment_id           UUID          NOT NULL PRIMARY KEY,
    state                VARCHAR(20)   NOT NULL,
    debtor_account_id    UUID          NOT NULL,
    biller_ref_number    VARCHAR(64)   NOT NULL,
    invoice_reference    VARCHAR(128)  NOT NULL,
    execution_date       DATE          NOT NULL,
    amount_value         DECIMAL(18,4) NOT NULL,
    amount_ccy           VARCHAR(3)    NOT NULL,
    batch_id             UUID,
    external_status_code VARCHAR(255),
    reason               VARCHAR(255),
    idempotency_key      VARCHAR(80)   NOT NULL,
    version              INTEGER       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_payment_amount_positive CHECK (amount_value > 0)
);

CREATE INDEX IF NOT EXISTS idx_payment_batch_id ON payments (batch_id);
CREATE INDEX IF NOT EXISTS idx_payment_state    ON payments (state);

CREATE TABLE IF NOT EXISTS outbox (
    id           BIGSERIAL    PRIMARY KEY,
    topic        VARCHAR(120) NOT NULL,
    message_key  VARCHAR(255) NOT NULL,
    payload_json TEXT         NOT NULL,
    state        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_outbox_state ON outbox (state, id);

CREATE TABLE IF NOT EXISTS processed_events (
    id           BIGSERIAL    PRIMARY KEY,
    handler      VARCHAR(80)  NOT NULL,
    event_id     VARCHAR(120) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_handler_event UNIQUE (handler, event_id)
);

CREATE TABLE IF NOT EXISTS retries (
    id              BIGSERIAL    PRIMARY KEY,
    batch_id        VARCHAR(64)  NOT NULL,
    attempt         INTEGER      NOT NULL,
    next_attempt_at TIMESTAMPTZ  NOT NULL,
    backoff_ms      BIGINT       NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    reason          VARCHAR(256),
    CONSTRAINT pk_batch_attempt UNIQUE (batch_id, attempt)
);
