-- Auth hardening P0: per-account login lockout (see Features/Auth_Hardening.md)

ALTER TABLE users
    ADD COLUMN failed_login_count INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until DATETIME(6) NULL,
    ADD COLUMN last_failed_login_at DATETIME(6) NULL;
