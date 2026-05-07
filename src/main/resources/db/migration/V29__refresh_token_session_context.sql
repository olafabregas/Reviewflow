-- P1: device / IP / UA context + stable session_group_id for session APIs

ALTER TABLE refresh_tokens
  ADD COLUMN device_id VARCHAR(64) NULL,
  ADD COLUMN ip_created VARCHAR(45) NULL,
  ADD COLUMN user_agent_created VARCHAR(500) NULL,
  ADD COLUMN ip_last_seen VARCHAR(45) NULL,
  ADD COLUMN user_agent_last_seen VARCHAR(500) NULL,
  ADD COLUMN session_group_id BIGINT NULL;

CREATE INDEX idx_refresh_user_active ON refresh_tokens (user_id, revoked, expires_at);

UPDATE refresh_tokens SET session_group_id = id WHERE session_group_id IS NULL;
