-- ===========================================================================
-- V26: Add token_version to users
-- PRD: Session Lifecycle Hardening (Revocation Gap)
-- ===========================================================================
-- token_version: Integer stamped into every access token as the `ver` claim.
--   Incremented atomically on deactivation or force-logout to invalidate
--   all outstanding access tokens within one cache TTL cycle (≤30 seconds).
--   Starts at 1 for all existing users. Never null. Never zero.
-- No index required — always accessed via primary key.
-- ===========================================================================

ALTER TABLE users
  ADD COLUMN token_version INT NOT NULL DEFAULT 1;
