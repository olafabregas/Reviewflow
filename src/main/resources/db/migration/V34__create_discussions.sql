-- PRD-17: Discussion forum + notification dedup columns (MySQL 8 — no PostgreSQL-style partial indexes)

DROP TABLE IF EXISTS discussion_posts;
DROP TABLE IF EXISTS discussions;

CREATE TABLE discussions (
    id                          BIGINT        NOT NULL AUTO_INCREMENT,
    course_id                   BIGINT        NOT NULL,
    assignment_id               BIGINT        NULL,
    title                       VARCHAR(255)  NOT NULL,
    prompt                      TEXT          NOT NULL,
    due_at                      DATETIME(6)   NOT NULL,
    require_post_before_reading BOOLEAN       NOT NULL DEFAULT true,
    allow_anonymous             BOOLEAN       NOT NULL DEFAULT false,
    is_graded                   BOOLEAN       NOT NULL DEFAULT false,
    is_published                BOOLEAN       NOT NULL DEFAULT false,
    published_at                DATETIME(6)   NULL,
    created_by                  BIGINT        NOT NULL,
    created_at                  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                              ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_disc_course
        FOREIGN KEY (course_id) REFERENCES courses(id),
    CONSTRAINT fk_disc_assignment
        FOREIGN KEY (assignment_id) REFERENCES assignments(id)
        ON DELETE SET NULL,
    CONSTRAINT fk_disc_creator
        FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE INDEX idx_disc_course_published
    ON discussions(course_id, is_published, due_at);

CREATE TABLE discussion_posts (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    discussion_id  BIGINT        NOT NULL,
    parent_post_id BIGINT        NULL,
    author_id      BIGINT        NOT NULL,
    content        TEXT          NULL,
    word_count     INT           NOT NULL DEFAULT 0,
    is_pinned      BOOLEAN       NOT NULL DEFAULT false,
    is_deleted     BOOLEAN       NOT NULL DEFAULT false,
    active_initial_marker TINYINT GENERATED ALWAYS AS (
        CASE WHEN parent_post_id IS NULL AND is_deleted = false THEN 0 ELSE NULL END
    ) STORED,
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                 ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_dp_discussion
        FOREIGN KEY (discussion_id) REFERENCES discussions(id) ON DELETE CASCADE,
    CONSTRAINT fk_dp_parent
        FOREIGN KEY (parent_post_id) REFERENCES discussion_posts(id),
    CONSTRAINT fk_dp_author
        FOREIGN KEY (author_id) REFERENCES users(id)
);

CREATE INDEX idx_dp_discussion_cursor
    ON discussion_posts(discussion_id, parent_post_id, created_at, id);

CREATE INDEX idx_dp_replies
    ON discussion_posts(discussion_id, parent_post_id, created_at, id);

CREATE INDEX idx_dp_participation
    ON discussion_posts(discussion_id, author_id, parent_post_id, is_deleted);

CREATE UNIQUE INDEX uk_dp_active_initial_post
    ON discussion_posts(discussion_id, author_id, active_initial_marker);

ALTER TABLE notifications
    ADD COLUMN date_bucket DATE NULL
        COMMENT 'Calendar day for deduped reminder sends; NULL = not deduped',
    ADD COLUMN dedup_key VARCHAR(255) GENERATED ALWAYS AS (
        CASE
            WHEN date_bucket IS NOT NULL
            THEN CONCAT(
                CAST(user_id AS CHAR), ':', `type`, ':',
                COALESCE(CAST(target_id AS CHAR), '0'), ':',
                CAST(date_bucket AS CHAR))
            ELSE NULL
        END
    ) STORED;

CREATE UNIQUE INDEX uk_notification_dedup ON notifications (dedup_key);
