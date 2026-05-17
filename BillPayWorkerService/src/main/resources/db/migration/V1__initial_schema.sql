-- BillPayWorkerService: V1 - Khởi tạo schema ban đầu

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
