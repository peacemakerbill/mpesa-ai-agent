-- MySQL version of V3__business_accounts.sql. Same table and names as the PostgreSQL script.
CREATE TABLE business_accounts (
    id                     BINARY(16) PRIMARY KEY,
    owner_phone            VARCHAR(15) NOT NULL,
    label                  VARCHAR(80) NOT NULL,
    environment            VARCHAR(12) NOT NULL,
    shortcode_type         VARCHAR(12) NOT NULL,
    shortcode              VARCHAR(12) NOT NULL,
    till_number            VARCHAR(12) NOT NULL,
    account_reference      VARCHAR(12) NOT NULL,
    parent_id              BINARY(16),
    consumer_key_hint      VARCHAR(8),
    consumer_key_enc       VARCHAR(500),
    consumer_secret_enc    VARCHAR(500),
    initiator_name         VARCHAR(50),
    initiator_password_enc VARCHAR(500),
    passkey_enc            VARCHAR(700),
    verified_at            DATETIME(6),
    created_at             DATETIME(6) NOT NULL,
    updated_at             DATETIME(6) NOT NULL,
    CONSTRAINT uk_business_accounts_code UNIQUE (owner_phone, environment, shortcode, till_number, account_reference)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin;

CREATE INDEX idx_business_accounts_parent ON business_accounts (owner_phone, parent_id);
