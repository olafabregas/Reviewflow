-- PRD-03: email notifications
-- Add opt-in preference column for STANDARD email notifications.

ALTER TABLE users
  ADD COLUMN email_notifications_enabled BOOLEAN NOT NULL DEFAULT true;
