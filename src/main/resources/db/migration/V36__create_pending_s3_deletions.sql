CREATE TABLE pending_s3_deletions (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    s3_key            VARCHAR(500)  NOT NULL,
    entity_type       VARCHAR(50)   NOT NULL,
    reason            VARCHAR(100)  NOT NULL,
    retry_count       INT           NOT NULL DEFAULT 0,
    max_retries       INT           NOT NULL DEFAULT 5,
    created_at        DATETIME      NOT NULL DEFAULT NOW(),
    completed_at      DATETIME      NULL,
    last_attempted_at DATETIME      NULL,
    error_message     VARCHAR(500)  NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_psd_pending
    ON pending_s3_deletions(completed_at, retry_count, created_at);

CREATE INDEX idx_psd_key
    ON pending_s3_deletions(s3_key);
