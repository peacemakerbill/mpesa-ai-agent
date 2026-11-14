CREATE TABLE approval_codes (
    id         UUID PRIMARY KEY,
    phone      VARCHAR(15) NOT NULL,
    kind       VARCHAR(10) NOT NULL,
    target_id  UUID        NOT NULL,
    code_hash  VARCHAR(64) NOT NULL,
    attempts   INTEGER     NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_approval_codes_target ON approval_codes (phone, kind, target_id, created_at);
