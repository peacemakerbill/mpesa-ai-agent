-- What customers asked Daraja to do on a business account, from prepare to result. No secrets are stored here.
CREATE TABLE business_operations (
    id                          UUID PRIMARY KEY,
    owner_phone                 VARCHAR(15)  NOT NULL,
    account_id                  UUID         NOT NULL,
    account_label               VARCHAR(80)  NOT NULL,
    type                        VARCHAR(16)  NOT NULL,
    status                      VARCHAR(20)  NOT NULL,
    amount                      INTEGER      NOT NULL,
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
    expires_at                  TIMESTAMP WITH TIME ZONE,
    approved_at                 TIMESTAMP WITH TIME ZONE,
    completed_at                TIMESTAMP WITH TIME ZONE,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_business_operations_key UNIQUE (owner_phone, idempotency_key)
);

CREATE INDEX idx_business_operations_owner ON business_operations (owner_phone, created_at);
