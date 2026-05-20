-- AccountService: V1 - Consolidated Schema
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
    transaction_id              UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid() PRIMARY KEY,
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
    CONSTRAINT uk_tx_account_idem UNIQUE (account_id, request_fingerprint)
);

CREATE INDEX IF NOT EXISTS idx_tx_account  ON account_transaction (account_id);
CREATE INDEX IF NOT EXISTS idx_tx_occurred ON account_transaction (occurred_at);
CREATE INDEX IF NOT EXISTS idx_tx_type     ON account_transaction (type);
CREATE INDEX IF NOT EXISTS idx_tx_status   ON account_transaction (status);

CREATE TABLE IF NOT EXISTS transaction_ledger (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    transaction_group_id UUID         NOT NULL,
    account_id          UUID          NOT NULL REFERENCES account(id),
    entry_type          VARCHAR(8)    NOT NULL,
    amount              DECIMAL(19,4) NOT NULL,
    currency            VARCHAR(3)    NOT NULL,
    reference_type      VARCHAR(32),
    reference_id        VARCHAR(64),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by          VARCHAR(255),
    CONSTRAINT chk_ledger_entry_type CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_ledger_amount_positive CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_ledger_group ON transaction_ledger (transaction_group_id);
CREATE INDEX IF NOT EXISTS idx_ledger_account ON transaction_ledger (account_id);
CREATE INDEX IF NOT EXISTS idx_ledger_created ON transaction_ledger (created_at);

INSERT INTO account (
    id, customer_id, account_number, account_type, account_sub_type,
    status, currency, balance, version, created_at, updated_at
) VALUES (
    '00000000-0000-0000-0000-000000000099',
    'SYSTEM',
    '0000000000',
    'CHEQUING',
    'BUSINESS',
    'ACTIVE',
    'VND',
    0,
    0,
    now(),
    now()
) ON CONFLICT (id) DO NOTHING;

CREATE OR REPLACE FUNCTION check_ledger_balance()
RETURNS TRIGGER AS $$
DECLARE
    balance DECIMAL(19,4);
BEGIN
    SELECT COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END), 0) -
           COALESCE(SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END), 0)
    INTO balance
    FROM transaction_ledger
    WHERE transaction_group_id = NEW.transaction_group_id;

    IF balance != 0 THEN
        RAISE EXCEPTION 'Ledger out of balance for group %%: difference is %%', NEW.transaction_group_id, balance;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_check_ledger_balance
    AFTER INSERT OR UPDATE ON transaction_ledger
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION check_ledger_balance();

CREATE TABLE IF NOT EXISTS idempotency_record (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    service_name VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    response_status INTEGER,
    response_snapshot TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_idempotency_created_at ON idempotency_record (created_at);
