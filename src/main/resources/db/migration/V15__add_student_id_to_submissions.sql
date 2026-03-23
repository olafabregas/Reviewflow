-- V15: allow submissions to be owned by either team or student.
-- WARNING: This migration is effectively irreversible after individual submissions exist.

ALTER TABLE submissions
  MODIFY COLUMN team_id BIGINT NULL,
  ADD COLUMN student_id BIGINT NULL,
  ADD CONSTRAINT fk_submissions_student FOREIGN KEY (student_id) REFERENCES users(id),
  ADD CONSTRAINT chk_submission_owner
    CHECK (
      (team_id IS NOT NULL AND student_id IS NULL) OR
      (team_id IS NULL AND student_id IS NOT NULL)
    );

CREATE INDEX idx_submissions_student_assignment
  ON submissions(student_id, assignment_id);
