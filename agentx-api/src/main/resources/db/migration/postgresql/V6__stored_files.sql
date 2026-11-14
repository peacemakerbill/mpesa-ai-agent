-- Files kept in the database (agentx.storage.type=database). Empty until that setting is used.
CREATE TABLE stored_files (
    file_key   VARCHAR(160) PRIMARY KEY,
    data       BYTEA       NOT NULL,
    size_bytes BIGINT      NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_stored_files_expires ON stored_files (expires_at);
