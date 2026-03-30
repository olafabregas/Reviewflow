-- V20__add_system_admin_role.sql
-- PRD-09: Add SYSTEM_ADMIN role for platform operators
-- Author: Agent 1 (Database)
-- Date: 2026-03-30

-- Step 1: Add SYSTEM_ADMIN to the role ENUM
-- Note: MySQL ENUM modification is a table rebuild on older versions (<8.0.15)
-- This is acceptable during development. Flag for maintenance window before production deploy.
ALTER TABLE users
    MODIFY COLUMN role ENUM('STUDENT', 'INSTRUCTOR', 'ADMIN', 'SYSTEM_ADMIN') NOT NULL;

-- Step 2: Seed initial SYSTEM_ADMIN accounts
-- These accounts are created via migration only - never via API (enforced at application level)
-- Password hash is a placeholder - must be changed on first deployment/login (production requirement)
-- Using bcrypt with cost 12 and a static placeholder for seeding purposes

INSERT INTO users (email, password_hash, role, first_name, last_name, is_active, created_at, updated_at)
VALUES (
    'admin@reviewflow.com',
    '$2a$12$placeholder_change_on_first_deploy_VKzNWW3mPJN0zVQmHXQjy.I9QT0uvn/R2', -- 32-char placeholder
    'SYSTEM_ADMIN',
    'Platform',
    'Administrator',
    TRUE,
    NOW(),
    NOW()
)
ON DUPLICATE KEY UPDATE role = 'SYSTEM_ADMIN';

-- Step 3: Seed backup SYSTEM_ADMIN accounts (optional - may be commented out)
-- Backup operators for operational resilience (5-account pool supports backup rotation)
/*
INSERT INTO users (email, password_hash, role, first_name, last_name, is_active, created_at, updated_at)
VALUES (
    'ops-backup-1@reviewflow.com',
    '$2a$12$placeholder_change_on_first_deploy_VKzNWW3mPJN0zVQmHXQjy.I9QT0uvn/R2',
    'SYSTEM_ADMIN',
    'Backup',
    'Operator 1',
    FALSE,  -- inactive until first login (prevents accidental use)
    NOW(),
    NOW()
)
ON DUPLICATE KEY UPDATE role = 'SYSTEM_ADMIN';

INSERT INTO users (email, password_hash, role, first_name, last_name, is_active, created_at, updated_at)
VALUES (
    'ops-backup-2@reviewflow.com',
    '$2a$12$placeholder_change_on_first_deploy_VKzNWW3mPJN0zVQmHXQjy.I9QT0uvn/R2',
    'SYSTEM_ADMIN',
    'Backup',
    'Operator 2',
    FALSE,
    NOW(),
    NOW()
)
ON DUPLICATE KEY UPDATE role = 'SYSTEM_ADMIN';
*/

-- Step 4: Audit the initial account creation
-- This entry is permanent and immutable - documents the creation action and reason
INSERT INTO audit_log (actor_id, action, target_type, target_id, metadata, created_at)
SELECT
    id,
    'SYSTEM_ADMIN_CREATED',
    'USER',
    id,
    JSON_OBJECT('reason', 'Initial platform operator account', 'migration', 'V20'),
    NOW()
FROM users
WHERE email = 'admin@reviewflow.com'
ON DUPLICATE KEY UPDATE created_at = created_at;  -- prevent duplicate inserts on re-run

-- Backup audit entries (uncomment if backup accounts are seeded)
/*
INSERT INTO audit_log (actor_id, action, target_type, target_id, metadata, created_at)
SELECT
    id,
    'SYSTEM_ADMIN_CREATED',
    'USER',
    id,
    JSON_OBJECT('reason', 'Backup platform operator account', 'migration', 'V20'),
    NOW()
FROM users
WHERE email IN ('ops-backup-1@reviewflow.com', 'ops-backup-2@reviewflow.com')
ON DUPLICATE KEY UPDATE created_at = created_at;
*/
