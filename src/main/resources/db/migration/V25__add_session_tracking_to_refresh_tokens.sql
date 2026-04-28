-- ===========================================================================
-- V25: Add session tracking columns to refresh_tokens
-- PRD: Session Lifecycle Hardening (G-3, G-4)
-- ===========================================================================
-- last_used_at: Tracks when the refresh token was last used. NULL on first
--   creation (before first refresh). Used for idle session timeout (2h).
-- session_issued_at: Anchors the absolute session ceiling (12h). Set at
--   initial login, carried forward on every token rotation. DEFAULT ensures
--   existing rows get a valid timestamp.
-- ===========================================================================

ALTER TABLE refresh_tokens
  ADD COLUMN last_used_at DATETIME(6) NULL;

ALTER TABLE refresh_tokens
  ADD COLUMN session_issued_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
