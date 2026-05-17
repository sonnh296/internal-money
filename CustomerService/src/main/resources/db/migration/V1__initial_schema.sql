-- CustomerService: V1 - Khởi tạo schema ban đầu

CREATE TABLE IF NOT EXISTS customers (
    id                  BIGSERIAL    PRIMARY KEY,
    version             INTEGER      NOT NULL DEFAULT 0,
    first_name          VARCHAR(255) NOT NULL,
    last_name           VARCHAR(255) NOT NULL,
    email               VARCHAR(255) NOT NULL,
    phone               VARCHAR(255),
    address             VARCHAR(255) NOT NULL,
    external_id         VARCHAR(255) NOT NULL UNIQUE,
    kyc_status          VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    active              BOOLEAN      NOT NULL DEFAULT true,
    request_fingerprint VARCHAR(255) NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uniq_customers_email       UNIQUE (email),
    CONSTRAINT uniq_customers_external_id UNIQUE (external_id)
);
