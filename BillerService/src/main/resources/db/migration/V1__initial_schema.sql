-- BillerService: V1 - Consolidated Schema

CREATE TABLE IF NOT EXISTS service_packages (
    id               UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name             VARCHAR(255)  NOT NULL,
    category         VARCHAR(255)  NOT NULL,
    reference_number VARCHAR(255)  NOT NULL UNIQUE,
    monthly_amount   DECIMAL(18,4) NOT NULL,
    currency         VARCHAR(3)    NOT NULL DEFAULT 'VND',
    description      VARCHAR(512),
    status           VARCHAR(50)   NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_package_amount_positive CHECK (monthly_amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_sp_status  ON service_packages (status);
CREATE INDEX IF NOT EXISTS idx_sp_ref_num ON service_packages (reference_number);

CREATE TABLE IF NOT EXISTS billers (
    id               UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    customer_id      VARCHAR(255) NOT NULL,
    name             VARCHAR(255) NOT NULL,
    reference_number VARCHAR(255) NOT NULL,
    category         VARCHAR(255) NOT NULL,
    status           VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_biller_owner_ref UNIQUE (customer_id, reference_number)
);

CREATE INDEX IF NOT EXISTS idx_biller_customer ON billers (customer_id);

CREATE TABLE IF NOT EXISTS subscriptions (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL,
    package_id  UUID        NOT NULL REFERENCES service_packages(id),
    status      VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_sub_customer_package UNIQUE (customer_id, package_id)
);

CREATE INDEX IF NOT EXISTS idx_sub_customer ON subscriptions (customer_id);
CREATE INDEX IF NOT EXISTS idx_sub_package  ON subscriptions (package_id);

CREATE TABLE IF NOT EXISTS invoices (
    id                     UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    subscription_id        UUID          NOT NULL REFERENCES subscriptions(id),
    customer_id            VARCHAR(64)   NOT NULL,
    package_id             UUID          NOT NULL REFERENCES service_packages(id),
    biller_reference_number VARCHAR(64)  NOT NULL,
    amount                 DECIMAL(18,4) NOT NULL,
    currency               VARCHAR(3)    NOT NULL DEFAULT 'VND',
    due_date               DATE          NOT NULL,
    status                 VARCHAR(50)   NOT NULL DEFAULT 'PENDING',
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_invoice_amount_positive CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_inv_customer     ON invoices (customer_id);
CREATE INDEX IF NOT EXISTS idx_inv_status       ON invoices (status);
CREATE INDEX IF NOT EXISTS idx_inv_subscription ON invoices (subscription_id);
