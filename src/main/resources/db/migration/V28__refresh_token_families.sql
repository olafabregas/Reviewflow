-- P1 Auth Hardening: refresh token families (reuse revokes one family only)

ALTER TABLE refresh_tokens
  ADD COLUMN family_id CHAR(36) NULL,
  ADD COLUMN parent_token_hash VARCHAR(64) NULL;

UPDATE refresh_tokens SET family_id = UUID() WHERE family_id IS NULL;

ALTER TABLE refresh_tokens
  MODIFY COLUMN family_id CHAR(36) NOT NULL,
  ADD INDEX idx_refresh_family (family_id);
