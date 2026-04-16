-- V21__create_assignment_groups.sql
-- PRD-10: Add assignment groups and backfill all existing courses/assignments
-- WARNING: irreversible backfill migration. Existing assignments are moved into Uncategorized groups.
-- Author: Agent 1 (Database)
-- Date: 2026-04-16

CREATE TABLE assignment_groups (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    weight DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    drop_lowest_n INT NOT NULL DEFAULT 0,
    display_order INT NOT NULL DEFAULT 0,
    is_uncategorized BOOLEAN NOT NULL DEFAULT FALSE,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_assignment_groups_course
        FOREIGN KEY (course_id) REFERENCES courses(id),
    CONSTRAINT fk_assignment_groups_created_by
        FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT chk_assignment_groups_weight
        CHECK (weight >= 0 AND weight <= 100),
    CONSTRAINT chk_assignment_groups_drop_lowest_n
        CHECK (drop_lowest_n >= 0)
);

CREATE INDEX idx_ag_course ON assignment_groups(course_id);

ALTER TABLE assignments
    ADD COLUMN group_id BIGINT NULL;

ALTER TABLE assignments
    ADD CONSTRAINT fk_assignments_group
        FOREIGN KEY (group_id) REFERENCES assignment_groups(id);

CREATE INDEX idx_assignment_group ON assignments(group_id);

INSERT INTO assignment_groups (
    course_id,
    name,
    weight,
    drop_lowest_n,
    display_order,
    is_uncategorized,
    created_by,
    created_at,
    updated_at
)
SELECT
    c.id,
    'Uncategorized',
    0.00,
    0,
    999,
    TRUE,
    c.created_by,
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
FROM courses c;

UPDATE assignments a
JOIN courses c ON a.course_id = c.id
JOIN assignment_groups ag ON ag.course_id = c.id AND ag.is_uncategorized = TRUE
SET a.group_id = ag.id
WHERE a.group_id IS NULL;

ALTER TABLE assignments
    MODIFY COLUMN group_id BIGINT NOT NULL;