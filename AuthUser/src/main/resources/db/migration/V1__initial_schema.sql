-- AuthUser: V1 - Khởi tạo schema ban đầu

CREATE TABLE IF NOT EXISTS auth_users (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    email               VARCHAR(254)  NOT NULL UNIQUE,
    customer_id         VARCHAR(64)   NOT NULL UNIQUE,
    password_hash       VARCHAR(255)  NOT NULL,
    enabled             BOOLEAN       NOT NULL DEFAULT true,
    failed_login_count  INTEGER       NOT NULL DEFAULT 0,
    locked_until        TIMESTAMP,
    last_login_at       TIMESTAMP,
    permissions         VARCHAR(1024) NOT NULL DEFAULT '',
    role                VARCHAR(32)   NOT NULL DEFAULT 'CUSTOMER',
    version             BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    token       VARCHAR(128) NOT NULL UNIQUE,
    user_id     UUID         NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    expires_at  TIMESTAMP    NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_refresh_token_user ON refresh_tokens (user_id);
