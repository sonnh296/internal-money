-- Tạo bảng lưu idempotency keys kèm theo snapshot response
CREATE TABLE IF NOT EXISTS idempotency_record (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    service_name VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    response_status INTEGER,
    response_snapshot TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Tự động dọn dẹp các key quá cũ (tuỳ chọn, có thể viết cronjob sau)
CREATE INDEX IF NOT EXISTS idx_idempotency_created_at ON idempotency_record (created_at);
