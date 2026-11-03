-- Customer profiles (sign up, name, email, address, location, picture), saved email recipients, and the email log.
CREATE TABLE user_profiles (
    id                      UUID PRIMARY KEY,
    phone                   VARCHAR(15)  NOT NULL,
    status                  VARCHAR(10)  NOT NULL,
    first_name              VARCHAR(60),
    middle_name             VARCHAR(60),
    last_name               VARCHAR(60),
    email                   VARCHAR(254),
    email_verified          BOOLEAN      NOT NULL DEFAULT FALSE,
    email_verified_at       TIMESTAMP WITH TIME ZONE,
    pending_email           VARCHAR(254),
    email_code_hash         VARCHAR(64),
    email_code_expires_at   TIMESTAMP WITH TIME ZONE,
    email_code_attempts     INTEGER      NOT NULL DEFAULT 0,
    email_code_sent_at      TIMESTAMP WITH TIME ZONE,
    address                 VARCHAR(200),
    city                    VARCHAR(80),
    county                  VARCHAR(80),
    country                 VARCHAR(80),
    latitude                DOUBLE PRECISION,
    longitude               DOUBLE PRECISION,
    picture_key             VARCHAR(120),
    picture_type            VARCHAR(40),
    picture_updated_at      TIMESTAMP WITH TIME ZONE,
    notify_sign_in          BOOLEAN      NOT NULL DEFAULT TRUE,
    notify_payments         BOOLEAN      NOT NULL DEFAULT TRUE,
    last_sign_in_mail_at    TIMESTAMP WITH TIME ZONE,
    signup_expires_at       TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_user_profiles_phone UNIQUE (phone)
);

CREATE TABLE email_recipients (
    id          UUID PRIMARY KEY,
    owner_phone VARCHAR(15)  NOT NULL,
    email       VARCHAR(254) NOT NULL,
    name        VARCHAR(80),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_email_recipients_owner_email UNIQUE (owner_phone, email)
);

CREATE TABLE email_log (
    id          UUID PRIMARY KEY,
    owner_phone VARCHAR(15)  NOT NULL,
    kind        VARCHAR(16)  NOT NULL,
    recipients  VARCHAR(500) NOT NULL,
    subject     VARCHAR(200) NOT NULL,
    status      VARCHAR(8)   NOT NULL,
    error       VARCHAR(300),
    attachment  VARCHAR(120),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_email_log_owner ON email_log (owner_phone, created_at);
