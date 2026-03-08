-- V10__fix_notifications_table.sql  (use your next version number)

-- Drop the foreign key constraint on user_id if it exists
ALTER TABLE notifications
    DROP FOREIGN KEY IF EXISTS fk_notifications_user;

-- Ensure user_id is a plain BIGINT (not a FK)
ALTER TABLE notifications
    MODIFY COLUMN user_id   BIGINT       NOT NULL,
    MODIFY COLUMN type      VARCHAR(50)  NOT NULL,
    MODIFY COLUMN title     VARCHAR(150) NOT NULL DEFAULT '',
    MODIFY COLUMN action_url VARCHAR(500);

-- Add indexes for query performance
CREATE INDEX IF NOT EXISTS idx_notifications_user_id
    ON notifications(user_id);

CREATE INDEX IF NOT EXISTS idx_notifications_is_read
    ON notifications(is_read);

CREATE INDEX IF NOT EXISTS idx_notifications_created_at
    ON notifications(created_at);