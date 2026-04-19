-- V24__add_instructor_graded_type.sql
-- PRD-12: Instructor-Graded Assessments
-- WARNING: Extending enum values is forward-only in deployed environments.

ALTER TABLE assignments
    MODIFY COLUMN submission_type
    ENUM('INDIVIDUAL', 'TEAM', 'INSTRUCTOR_GRADED') NOT NULL DEFAULT 'INDIVIDUAL';

ALTER TABLE assignments
    ADD COLUMN max_score DECIMAL(6,2) NULL;

CREATE TABLE instructor_scores (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT       NOT NULL,
    student_id    BIGINT       NULL,
    team_id       BIGINT       NULL,
    score         DECIMAL(6,2) NOT NULL,
    max_score     DECIMAL(6,2) NOT NULL,
    comment       TEXT         NULL,
    is_published  BOOLEAN      NOT NULL DEFAULT FALSE,
    published_at  DATETIME     NULL,
    entered_by    BIGINT       NOT NULL,
    entered_at    DATETIME     NOT NULL DEFAULT NOW(),
    updated_at    DATETIME     NOT NULL DEFAULT NOW()
                               ON UPDATE NOW(),
    PRIMARY KEY (id),
    CONSTRAINT fk_is_assignment
        FOREIGN KEY (assignment_id) REFERENCES assignments(id),
    CONSTRAINT fk_is_student
        FOREIGN KEY (student_id) REFERENCES users(id),
    CONSTRAINT fk_is_team
        FOREIGN KEY (team_id) REFERENCES teams(id),
    CONSTRAINT fk_is_entered_by
        FOREIGN KEY (entered_by) REFERENCES users(id),
    CONSTRAINT chk_is_owner
        CHECK (
            (student_id IS NOT NULL AND team_id IS NULL) OR
            (team_id IS NOT NULL AND student_id IS NULL)
        ),
    CONSTRAINT chk_is_score_range
        CHECK (score >= 0 AND score <= max_score),
    UNIQUE KEY uk_is_assignment_student (assignment_id, student_id),
    UNIQUE KEY uk_is_assignment_team (assignment_id, team_id)
);

CREATE INDEX idx_is_assignment ON instructor_scores(assignment_id, is_published);
CREATE INDEX idx_is_student ON instructor_scores(student_id, is_published);