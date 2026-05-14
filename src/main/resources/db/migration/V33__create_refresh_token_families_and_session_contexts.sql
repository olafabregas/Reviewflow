-- Align DB with JPA: RefreshTokenFamily → refresh_token_families, SessionContext → session_contexts.
-- V28 only added family_id on refresh_tokens; entities were added later without a matching migration.

CREATE TABLE refresh_token_families (
    id              CHAR(36)     NOT NULL,
    user_id         BIGINT       NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    revoked_at      DATETIME(6)  NULL,
    revoke_reason   VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_rtf_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_rtf_user ON refresh_token_families (user_id);

-- One row per distinct family_id already stored on refresh_tokens
INSERT INTO refresh_token_families (id, user_id, created_at, revoked_at, revoke_reason)
SELECT family_id,
       MIN(user_id),
       MIN(COALESCE(created_at, session_issued_at)),
       NULL,
       NULL
FROM refresh_tokens
WHERE family_id IS NOT NULL
  AND TRIM(family_id) <> ''
GROUP BY family_id;

ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_family
        FOREIGN KEY (family_id) REFERENCES refresh_token_families (id);

CREATE TABLE session_contexts (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    family_id            CHAR(36)     NOT NULL,
    device_id            VARCHAR(64)  NULL,
    user_agent           VARCHAR(1024) NULL,
    ip_address           VARCHAR(45)  NULL,
    created_at_context   DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_session_contexts_family
        FOREIGN KEY (family_id) REFERENCES refresh_token_families (id)
);

CREATE INDEX idx_session_contexts_family ON session_contexts (family_id);
