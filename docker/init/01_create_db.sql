-- ─────────────────────────────────────────────────────────────
-- ReviewFlow — MySQL Initialisation Script
-- Runs automatically on first container boot via
-- /docker-entrypoint-initdb.d/ (MySQL Docker image feature)
--
-- What this does:
--   1. Creates the application database
--   2. Creates the application user with a strong password
--   3. Grants minimum required privileges
--
-- What this does NOT do:
--   - Schema creation (Flyway owns V1–V27 migrations)
--   - Seed data (Flyway migrations handle seeding)
--   - Root password (set via MYSQL_ROOT_PASSWORD env var)
--
-- IMPORTANT: This script runs exactly once — on first container
-- boot when the data volume is empty. Re-creating the container
-- does NOT re-run this script if the volume already has data.
-- ─────────────────────────────────────────────────────────────

-- Create application database
CREATE DATABASE IF NOT EXISTS reviewflow
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- Create application user
-- Password injected from MYSQL_PASSWORD env var at runtime
-- See docker-compose file — MYSQL_PASSWORD=${DB_PASSWORD}
CREATE USER IF NOT EXISTS 'reviewflow'@'%'
    IDENTIFIED BY '${MYSQL_PASSWORD}';

-- Grant minimum required privileges
-- SELECT, INSERT, UPDATE, DELETE  — application CRUD
-- CREATE, ALTER, DROP, INDEX      — Flyway migrations
-- REFERENCES                      — foreign key constraints
GRANT SELECT, INSERT, UPDATE, DELETE,
      CREATE, ALTER, DROP, INDEX, REFERENCES
    ON reviewflow.*
    TO 'reviewflow'@'%';

-- Apply grants immediately
FLUSH PRIVILEGES;
