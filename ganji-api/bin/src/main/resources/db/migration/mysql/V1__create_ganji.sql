-- MySQL version of V1__create_ganji.sql. Same tables and names as the PostgreSQL script.
-- utf8mb4_bin so codes, keys and idempotency keys compare exactly, as they do in PostgreSQL.
CREATE TABLE devices (
    id           BINARY(16) PRIMARY KEY,
    phone        VARCHAR(15) NOT NULL,
    device_id    VARCHAR(64) NOT NULL,
    device_name  VARCHAR(80),
    revoked      BOOLEAN     NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_devices_phone_device UNIQUE (phone, device_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin;

CREATE TABLE link_codes (
    id         BINARY(16) PRIMARY KEY,
    phone      VARCHAR(15) NOT NULL,
    code_hash  VARCHAR(64) NOT NULL,
    attempts   INTEGER     NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6),
    created_at DATETIME(6) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin;

CREATE INDEX idx_link_codes_phone_created ON link_codes (phone, created_at);

CREATE TABLE wallet_actions (
    id                 BINARY(16) PRIMARY KEY,
    idempotency_key    VARCHAR(100) NOT NULL,
    phone              VARCHAR(15)  NOT NULL,
    type               VARCHAR(20)  NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    amount             INTEGER      NOT NULL,
    label              VARCHAR(80),
    source             VARCHAR(12)  NOT NULL,
    details            VARCHAR(2000) NOT NULL,
    receipt            VARCHAR(30),
    approved_device_id VARCHAR(64),
    approved_at        DATETIME(6),
    reported_at        DATETIME(6),
    expires_at         DATETIME(6) NOT NULL,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    CONSTRAINT uk_wallet_actions_idempotency UNIQUE (idempotency_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin;

CREATE INDEX idx_wallet_actions_phone_created ON wallet_actions (phone, created_at);
