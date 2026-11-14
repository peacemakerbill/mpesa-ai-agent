CREATE TABLE approval_codes (
    id         BINARY(16) PRIMARY KEY,
    phone      VARCHAR(15) NOT NULL,
    kind       VARCHAR(10) NOT NULL,
    target_id  BINARY(16)  NOT NULL,
    code_hash  VARCHAR(64) NOT NULL,
    attempts   INTEGER     NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6),
    created_at DATETIME(6) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin;

CREATE INDEX idx_approval_codes_target ON approval_codes (phone, kind, target_id, created_at);
