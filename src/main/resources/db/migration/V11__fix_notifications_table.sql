-- Safe to not truncate — this migration just adjusts column properties
-- No data loss expected

-- Drop FK constraint if it exists from old @ManyToOne mapping
-- MySQL doesn't support IF EXISTS for DROP FOREIGN KEY, so we use a procedure
SET @constraint_name = (
    SELECT CONSTRAINT_NAME
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notifications'
      AND COLUMN_NAME = 'user_id'
      AND CONSTRAINT_NAME != 'PRIMARY'
    LIMIT 1
);

SET @drop_fk = IF(@constraint_name IS NOT NULL,
    CONCAT('ALTER TABLE notifications DROP FOREIGN KEY ', @constraint_name),
    'SELECT 1');

PREPARE stmt FROM @drop_fk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Fix column types to match updated entity
ALTER TABLE notifications
    MODIFY COLUMN user_id     BIGINT       NOT NULL,
    MODIFY COLUMN type        VARCHAR(50)  NOT NULL,
    MODIFY COLUMN title       VARCHAR(150) NOT NULL DEFAULT '',
    MODIFY COLUMN action_url  VARCHAR(500);

-- Drop old indexes if they exist (using dynamic SQL for compatibility)
SET @index_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notifications'
      AND INDEX_NAME = 'idx_notifications_user_read'
);

SET @drop_idx = IF(@index_exists > 0,
    'DROP INDEX idx_notifications_user_read ON notifications',
    'SELECT 1');

PREPARE stmt FROM @drop_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add indexes for query performance (using dynamic SQL for compatibility)
-- Index 1: idx_notifications_user_id
SET @idx1_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notifications'
      AND INDEX_NAME = 'idx_notifications_user_id'
);

SET @create_idx1 = IF(@idx1_exists = 0,
    'CREATE INDEX idx_notifications_user_id ON notifications(user_id)',
    'SELECT 1');

PREPARE stmt FROM @create_idx1;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Index 2: idx_notifications_is_read
SET @idx2_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notifications'
      AND INDEX_NAME = 'idx_notifications_is_read'
);

SET @create_idx2 = IF(@idx2_exists = 0,
    'CREATE INDEX idx_notifications_is_read ON notifications(is_read)',
    'SELECT 1');

PREPARE stmt FROM @create_idx2;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Index 3: idx_notifications_created_at
SET @idx3_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notifications'
      AND INDEX_NAME = 'idx_notifications_created_at'
);

SET @create_idx3 = IF(@idx3_exists = 0,
    'CREATE INDEX idx_notifications_created_at ON notifications(created_at)',
    'SELECT 1');

PREPARE stmt FROM @create_idx3;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
