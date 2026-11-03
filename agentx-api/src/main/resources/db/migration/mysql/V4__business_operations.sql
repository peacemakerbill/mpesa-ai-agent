-- MySQL version of V4__business_operations.sql. Same table and names as the PostgreSQL script.
CREATE TABLE business_operations (
    id                          BINARY(16) PRIMARY KEY,
    owner_phone                 VARCHAR(15)  NOT NULL,
    account_id                  BINARY(16)   NOT NULL,
    account_label               VARCHAR(80)  NOT NULL,
    type                        VARCHAR(16)  NOT NULL,
    status                      VARCHAR(20)  NOT NULL,
    amount                      INT          NOT NULL,
    summary                     VARCHAR(300) NOT NULL,
    params                      VARCHAR(2000) NOT NULL,
    source                      VARCHAR(12),
    idempotency_key             VARCHAR(64)  NOT NULL,
    callback_token              VARCHAR(40)  NOT NULL,
    originator_conversation_id  VARCHAR(64),
    conversation_id             VARCHAR(64),
    checkout_request_id         VARCHAR(64),
    receipt                     VARCHAR(30),
    result_text                 VARCHAR(2000),
    failure                     VARCHAR(500),
    approval_method             VARCHAR(20),
    expires_at                  DATETIME(6),
    approved_at                 DATETIME(6),
    completed_at                DATETIME(6),
    created_at                  DATETIME(6) NOT NULL,
    updated_at                  DATETIME(6) NOT NULL,
    CONSTRAINT uk_business_operations_key UNIQUE (owner_phone, idempotency_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin;

CREATE INDEX idx_business_operations_owner ON business_operations (owner_phone, created_at);
