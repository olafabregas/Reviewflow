-- PRD-02: profile pictures
-- Safe forward migration: add nullable URL column, no data backfill required.

ALTER TABLE users
  ADD COLUMN avatar_url VARCHAR(500) NULL;
