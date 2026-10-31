CREATE TABLE devices (
    id           UUID PRIMARY KEY,
    phone        VARCHAR(15) NOT NULL,
    device_id    VARCHAR(64) NOT NULL,
    device_name  VARCHAR(80),
    revoked      BOOLEAN     NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_devices_phone_device UNIQUE (phone, device_id)
);

CREATE TABLE link_codes (
    id         UUID PRIMARY KEY,
    phone      VARCHAR(15) NOT NULL,
    code_hash  VARCHAR(64) NOT NULL,
    attempts   INTEGER     NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_link_codes_phone_created ON link_codes (phone, created_at);

CREATE TABLE wallet_actions (
    id                 UUID PRIMARY KEY,
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
    approved_at        TIMESTAMP WITH TIME ZONE,
    reported_at        TIMESTAMP WITH TIME ZONE,
    expires_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_wallet_actions_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_wallet_actions_phone_created ON wallet_actions (phone, created_at);
