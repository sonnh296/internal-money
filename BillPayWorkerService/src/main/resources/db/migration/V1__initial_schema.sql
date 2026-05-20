-- BillPayWorkerService: V1 - Consolidated Schema
CREATE TABLE IF NOT EXISTS batches (
    batch_id   UUID        NOT NULL PRIMARY KEY,
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS batch_lines (
    id         BIGSERIAL   PRIMARY KEY,
    batch_id   UUID        NOT NULL REFERENCES batches(batch_id),
    payment_id UUID        NOT NULL,
    line_no    INTEGER     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_batch_lines_batch ON batch_lines (batch_id);

CREATE TABLE IF NOT EXISTS outbox (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    message_key VARCHAR(255),
    payload_json TEXT NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_outbox_state ON outbox (state);
