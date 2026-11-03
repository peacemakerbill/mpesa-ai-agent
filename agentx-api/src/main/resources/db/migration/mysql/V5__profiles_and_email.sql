-- MySQL version of V5__profiles_and_email.sql. Same tables and names as the PostgreSQL script.
CREATE TABLE user_profiles (
    id                      BINARY(16) PRIMARY KEY,
    phone                   VARCHAR(15)  NOT NULL,
    status                  VARCHAR(10)  NOT NULL,
    first_name              VARCHAR(60),
    middle_name             VARCHAR(60),
    last_name               VARCHAR(60),
    email                   VARCHAR(254),
    email_verified          BOOLEAN      NOT NULL DEFAULT FALSE,
    email_verified_at       DATETIME(6),
    pending_email           VARCHAR(254),
    email_code_hash         VARCHAR(64),
    email_code_expires_at   DATETIME(6),
    email_code_attempts     INT          NOT NULL DEFAULT 0,
    email_code_sent_at      DATETIME(6),
    address                 VARCHAR(200),
    city                    VARCHAR(80),
    county                  VARCHAR(80),
    country                 VARCHAR(80),
    latitude                DOUBLE,
    longitude               DOUBLE,
    picture_key             VARCHAR(120),
    picture_type            VARCHAR(40),
    picture_updated_at      DATETIME(6),
    notify_sign_in          BOOLEAN      NOT NULL DEFAULT TRUE,
    notify_payments         BOOLEAN      NOT NULL DEFAULT TRUE,
    last_sign_in_mail_at    DATETIME(6),
    signup_expires_at       DATETIME(6),
    created_at              DATETIME(6) NOT NULL,
    updated_at              DATETIME(6) NOT NULL,
    CONSTRAINT uk_user_profiles_phone UNIQUE (phone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin;

CREATE TABLE email_recipients (
    id          BINARY(16) PRIMARY KEY,
    owner_phone VARCHAR(15)  NOT NULL,
    email       VARCHAR(254) NOT NULL,
    name        VARCHAR(80),
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    CONSTRAINT uk_email_recipients_owner_email UNIQUE (owner_phone, email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin;

CREATE TABLE email_log (
    id          BINARY(16) PRIMARY KEY,
    owner_phone VARCHAR(15)  NOT NULL,
    kind        VARCHAR(16)  NOT NULL,
    recipients  VARCHAR(500) NOT NULL,
    subject     VARCHAR(200) NOT NULL,
    status      VARCHAR(8)   NOT NULL,
    error       VARCHAR(300),
    attachment  VARCHAR(120),
    created_at  DATETIME(6) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin;

CREATE INDEX idx_email_log_owner ON email_log (owner_phone, created_at);
