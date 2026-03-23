-- V14: add assignment submission type with safe default for new records.
-- Existing assignments are backfilled to TEAM to preserve legacy team-based behavior.

ALTER TABLE assignments
  ADD COLUMN submission_type ENUM('INDIVIDUAL', 'TEAM') NOT NULL DEFAULT 'INDIVIDUAL',
  MODIFY COLUMN max_team_size INT NULL;

UPDATE assignments
SET submission_type = 'TEAM';
