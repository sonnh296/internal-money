-- Sổ cái double-entry: mỗi nhóm giao dịch có tổng DEBIT = CREDIT
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

-- Tài khoản đối ứng nội bộ cho bút toán single-sided (debit/credit bill pay)
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
