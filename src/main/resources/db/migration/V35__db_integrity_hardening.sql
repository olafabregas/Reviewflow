-- V35__db_integrity_hardening.sql
-- Database integrity hardening: optimistic locking, missing indexes,
-- cascade safety, and audit timestamps.
-- After V34 (discussions). PRD-16 content delivery moves to V36.

-- Phase 1: Optimistic locking columns
ALTER TABLE submissions
    ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0
    COMMENT 'JPA optimistic lock version — distinct from versionNumber (document revision)';

ALTER TABLE evaluations
    ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0
    COMMENT 'JPA optimistic lock version';

ALTER TABLE instructor_scores
    ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0
    COMMENT 'JPA optimistic lock version';

ALTER TABLE rubric_scores
    ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0
    COMMENT 'JPA optimistic lock version';

ALTER TABLE extension_requests
    ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0
    COMMENT 'JPA optimistic lock version';

-- Phase 2: Missing indexes
CREATE INDEX idx_conv_course ON conversations(course_id);

-- Phase 3: Submission audit timestamps
ALTER TABLE submissions
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);

UPDATE submissions
SET created_at = COALESCE(uploaded_at, created_at),
    updated_at = COALESCE(uploaded_at, updated_at);
