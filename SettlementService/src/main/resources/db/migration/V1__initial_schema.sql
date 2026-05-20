-- SettlementService: V1 - Consolidated Schema
CREATE TABLE IF NOT EXISTS bill_batch_settlement (
    batch_id   UUID        NOT NULL PRIMARY KEY,
    status     VARCHAR(20) NOT NULL,
    retry_count INTEGER    NOT NULL DEFAULT 0,
    pain001_file_name VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

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
