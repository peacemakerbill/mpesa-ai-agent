CREATE TABLE mpesa_operations (
    id                         UUID PRIMARY KEY,
    idempotency_key            VARCHAR(100) NOT NULL,
    type                       VARCHAR(30)  NOT NULL,
    status                     VARCHAR(20)  NOT NULL,
    requested_by               VARCHAR(15)  NOT NULL,
    approved_by                VARCHAR(15),
    recipient_phone            VARCHAR(15),
    amount                     INTEGER,
    target_receipt             VARCHAR(30),
    related_payment_id         UUID,
    reason                     VARCHAR(255),
    originator_conversation_id VARCHAR(60),
    conversation_id            VARCHAR(60),
    mpesa_receipt              VARCHAR(30),
    result_code                VARCHAR(20),
    result_desc                VARCHAR(255),
    result_data                VARCHAR(4000),
    created_at                 TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                 TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_operations_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uk_operations_originator_conversation UNIQUE (originator_conversation_id)
);

CREATE INDEX idx_operations_status_created ON mpesa_operations (status, created_at);
CREATE INDEX idx_operations_requested_by ON mpesa_operations (requested_by, created_at);
CREATE INDEX idx_operations_related_payment ON mpesa_operations (related_payment_id);
CREATE INDEX idx_operations_conversation ON mpesa_operations (conversation_id);

CREATE TABLE c2b_transactions (
    id              UUID PRIMARY KEY,
    trans_id        VARCHAR(30)    NOT NULL,
    trans_type      VARCHAR(30),
    trans_time      VARCHAR(20),
    amount          NUMERIC(12, 2) NOT NULL,
    shortcode       VARCHAR(20),
    bill_ref_number VARCHAR(50),
    msisdn          VARCHAR(100),
    payer_name      VARCHAR(150),
    org_balance     VARCHAR(30),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_c2b_trans_id UNIQUE (trans_id)
);

CREATE INDEX idx_c2b_bill_ref ON c2b_transactions (bill_ref_number);
