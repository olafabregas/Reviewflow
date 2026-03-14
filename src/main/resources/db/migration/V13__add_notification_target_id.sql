-- Add target_id column to notifications table for action URL rewriting
-- Stores the ID of the resource (team, submission, etc.) that the notification points to
-- Used to rewrite action URL templates like "/teams/{id}" with hashed IDs

ALTER TABLE notifications
ADD COLUMN target_id BIGINT NULL COMMENT 'Optional ID of the target resource for action URL rewriting';

-- No index needed - this field is only read when serving notifications, never queried
