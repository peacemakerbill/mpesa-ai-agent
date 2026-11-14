-- Files kept in the database (agentx.storage.type=database). Empty until that setting is used.
CREATE TABLE stored_files (
    file_key   VARCHAR(160) NOT NULL PRIMARY KEY,
    data       LONGBLOB     NOT NULL,
    size_bytes BIGINT       NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    expires_at DATETIME(6)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin;

CREATE INDEX idx_stored_files_expires ON stored_files (expires_at);
