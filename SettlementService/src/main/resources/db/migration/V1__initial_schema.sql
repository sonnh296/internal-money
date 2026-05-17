-- SettlementService: V1 - Khởi tạo schema ban đầu

CREATE TABLE IF NOT EXISTS bill_batch_settlement (
    id                 BIGSERIAL    PRIMARY KEY,
    batch_id           UUID         NOT NULL UNIQUE,
    status             VARCHAR(50)  NOT NULL,
    retry_count        INTEGER      NOT NULL DEFAULT 0,
    pain001_file_name  VARCHAR(255),
    central_reference  VARCHAR(255),
    last_error         VARCHAR(255),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ
);
