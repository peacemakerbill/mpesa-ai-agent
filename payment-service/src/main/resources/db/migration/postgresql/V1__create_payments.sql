CREATE TABLE payments (
    id                  UUID PRIMARY KEY,
    idempotency_key     VARCHAR(100) NOT NULL,
    phone               VARCHAR(15)  NOT NULL,
    amount              INTEGER      NOT NULL,
    account_reference   VARCHAR(12)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    merchant_request_id VARCHAR(60),
    checkout_request_id VARCHAR(60),
    mpesa_receipt       VARCHAR(30),
    result_code         VARCHAR(20),
    result_desc         VARCHAR(255),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_payments_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uk_payments_checkout_request_id UNIQUE (checkout_request_id)
);

CREATE INDEX idx_payments_phone_created ON payments (phone, created_at);
CREATE INDEX idx_payments_status_updated ON payments (status, updated_at);
