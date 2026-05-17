-- AccountService: V1 - Khởi tạo schema ban đầu
-- Xóa data cũ để migration sạch

-- Sequence cho account number (đảm bảo không collision dù dưới tải cao)
CREATE SEQUENCE IF NOT EXISTS account_number_seq START 100000001 INCREMENT 1;

CREATE TABLE IF NOT EXISTS account (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    customer_id         VARCHAR(255) NOT NULL UNIQUE,
    account_number      VARCHAR(20)  NOT NULL UNIQUE,
    account_type        VARCHAR(50)  NOT NULL,
    account_sub_type    VARCHAR(50)  NOT NULL,
    status              VARCHAR(50)  NOT NULL,
    currency            VARCHAR(3)   NOT NULL,
    nickname            VARCHAR(64),
    display_name        VARCHAR(64),
    balance             DECIMAL(19,4) NOT NULL DEFAULT 0,
    version             INTEGER      NOT NULL DEFAULT 0,
    request_fingerprint VARCHAR(128) UNIQUE,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now(),
    -- Ràng buộc DB: số dư không được âm, bảo vệ tầng DB tránh bug code
    CONSTRAINT chk_account_balance_non_negative CHECK (balance >= 0)
);

CREATE INDEX IF NOT EXISTS idx_account_customer    ON account (customer_id);
CREATE INDEX IF NOT EXISTS idx_account_status      ON account (status);
CREATE UNIQUE INDEX IF NOT EXISTS idx_account_fingerprint ON account (request_fingerprint)
    WHERE request_fingerprint IS NOT NULL;

CREATE TABLE IF NOT EXISTS account_hold (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    account_id          UUID        NOT NULL REFERENCES account(id),
    amount              DECIMAL(19,4) NOT NULL,
    status              VARCHAR(50)  NOT NULL,
    reason              VARCHAR(255),
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now(),
    release_at          TIMESTAMP,
    request_fingerprint VARCHAR(128) UNIQUE,
    CONSTRAINT chk_hold_amount_positive CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_hold_account ON account_hold (account_id);
CREATE INDEX IF NOT EXISTS idx_hold_status  ON account_hold (status);

CREATE TABLE IF NOT EXISTS account_transaction (
    id                          BIGSERIAL    PRIMARY KEY,
    transaction_id              UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    account_id                  UUID         NOT NULL REFERENCES account(id),
    type                        VARCHAR(32)  NOT NULL,
    status                      VARCHAR(32)  NOT NULL DEFAULT 'POSTED',
    amount                      DECIMAL(19,4) NOT NULL,
    currency                    VARCHAR(3)   NOT NULL DEFAULT 'VND',
    reason                      VARCHAR(256),
    flow_direction              VARCHAR(8),
    counterparty_name           VARCHAR(128),
    counterparty_account_number VARCHAR(32),
    reference_id                VARCHAR(64),
    balance_after               DECIMAL(19,4),
    occurred_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    request_fingerprint         VARCHAR(100),
    created_at                  TIMESTAMPTZ,
    updated_at                  TIMESTAMPTZ,
    version                     INTEGER      NOT NULL DEFAULT 0,
    -- Idempotency: mỗi tài khoản chỉ có một giao dịch với cùng fingerprint
    CONSTRAINT uk_tx_account_idem UNIQUE (account_id, request_fingerprint)
);

CREATE INDEX IF NOT EXISTS idx_tx_account  ON account_transaction (account_id);
CREATE INDEX IF NOT EXISTS idx_tx_occurred ON account_transaction (occurred_at);
CREATE INDEX IF NOT EXISTS idx_tx_type     ON account_transaction (type);
CREATE INDEX IF NOT EXISTS idx_tx_status   ON account_transaction (status);
