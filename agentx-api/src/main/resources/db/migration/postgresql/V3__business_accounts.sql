-- Business shortcodes and their Daraja credentials. The consumer key, consumer secret, initiator password and passkey
-- are stored locked (AES-GCM, text starting v1:). A branch has parent_id and empty credential columns.
-- till_number and account_reference hold an empty string when there is none, so the unique key works the same everywhere.
CREATE TABLE business_accounts (
    id                     UUID PRIMARY KEY,
    owner_phone            VARCHAR(15) NOT NULL,
    label                  VARCHAR(80) NOT NULL,
    environment            VARCHAR(12) NOT NULL,
    shortcode_type         VARCHAR(12) NOT NULL,
    shortcode              VARCHAR(12) NOT NULL,
    till_number            VARCHAR(12) NOT NULL,
    account_reference      VARCHAR(12) NOT NULL,
    parent_id              UUID,
    consumer_key_hint      VARCHAR(8),
    consumer_key_enc       VARCHAR(500),
    consumer_secret_enc    VARCHAR(500),
    initiator_name         VARCHAR(50),
    initiator_password_enc VARCHAR(500),
    passkey_enc            VARCHAR(700),
    verified_at            TIMESTAMP WITH TIME ZONE,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_business_accounts_code UNIQUE (owner_phone, environment, shortcode, till_number, account_reference)
);

CREATE INDEX idx_business_accounts_parent ON business_accounts (owner_phone, parent_id);
