-- ============================================================================
-- Manual Setup Script: System Admin and Regular Admin Account Creation
-- ============================================================================
-- Purpose: Create 1 SYSTEM_ADMIN and 2 ADMIN accounts without migration hassles
-- Date: 2026-04-04
-- Status: Run this script once manually in target database
-- ============================================================================

-- Safety check: Show existing admins before insertion
SELECT id, email, role, is_active FROM users WHERE role IN ('ADMIN', 'SYSTEM_ADMIN');

-- ============================================================================
-- SECTION 1: Create SYSTEM_ADMIN Account
-- ============================================================================
-- Super Admin: main_sysadmin@reviewflow.com
-- Role: SYSTEM_ADMIN (platform operator - infrastructure, cache, security audit)
-- Default password will be set on first login (bcrypt placeholder for now)

INSERT INTO users (email, password_hash, role, first_name, last_name, is_active, created_at, updated_at)
VALUES (
    'main_sysadmin@reviewflow.com',
    '$2a$12$BLoVKF/8YWLsjEoKNbGuVu.Q5Y1nVRzZMyJ1udi22PeS0WXsdTAia', -- 32-char bcrypt placeholder
    'SYSTEM_ADMIN',
    'Main',
    'System Administrator',
    TRUE,
    NOW(),
    NOW()
)
ON DUPLICATE KEY UPDATE 
    role = 'SYSTEM_ADMIN',
    is_active = TRUE,
    updated_at = NOW();

-- ============================================================================
-- SECTION 2: Create ADMIN Accounts (Academic Management)
-- ============================================================================
-- Regular Admin 1: humberadmin@reviewflow.com
-- Role: ADMIN (course management, grade oversight, announcements)

INSERT INTO users (email, password_hash, role, first_name, last_name, is_active, created_at, updated_at)
VALUES (
    'humberadmin@reviewflow.com',
    '$2a$12$ChangeOnFirstLogin2VKzNWW3mPJN0zVQmHXQjy.I9QT0uvn/R2', -- 32-char bcrypt placeholder
    'ADMIN',
    'Humber',
    'Administrator',
    TRUE,
    NOW(),
    NOW()
)
ON DUPLICATE KEY UPDATE 
    role = 'ADMIN',
    is_active = TRUE,
    updated_at = NOW();

-- Regular Admin 2: yorkadmin@reviewflow.com

INSERT INTO users (email, password_hash, role, first_name, last_name, is_active, created_at, updated_at)
VALUES (
    'yorkadmin@reviewflow.com',
    '$2a$12$dahkaaZT0UQiV/YikeJ4fu1FnjmMu44vVI5pUA5lCfHCH7Psa.rwu', -- 32-char bcrypt placeholder
    'ADMIN',
    'York',
    'Administrator',
    TRUE,
    NOW(),
    NOW()
)
ON DUPLICATE KEY UPDATE 
    role = 'ADMIN',
    is_active = TRUE,
    updated_at = NOW();

-- ============================================================================
-- SECTION 3: Audit Log Entries (Track Admin Creation)
-- ============================================================================
-- Log created SYSTEM_ADMIN
INSERT INTO audit_log (actor_id, action, target_type, target_id, metadata, created_at)
SELECT
    u.id,
    'ADMIN_CREATED',
    'USER',
    u.id,
    JSON_OBJECT('reason', 'Manual setup - SYSTEM_ADMIN (main)', 'script_version', '1.0', 'env', 'development'),
    NOW()
FROM users u
WHERE u.email = 'main_sysadmin@reviewflow.com'
AND NOT EXISTS (
    SELECT 1 FROM audit_log al 
    WHERE al.target_id = u.id AND al.action = 'ADMIN_CREATED' AND DATE(al.created_at) = CURDATE()
)
LIMIT 1;

-- Log created ADMIN accounts
INSERT INTO audit_log (actor_id, action, target_type, target_id, metadata, created_at)
SELECT
    u.id,
    'ADMIN_CREATED',
    'USER',
    u.id,
    JSON_OBJECT('reason', 'Manual setup - ADMIN (academic)', 'script_version', '1.0', 'env', 'development'),
    NOW()
FROM users u
WHERE u.email IN ('humberadmin@reviewflow.com', 'yorkadmin@reviewflow.com')
AND NOT EXISTS (
    SELECT 1 FROM audit_log al 
    WHERE al.target_id = u.id AND al.action = 'ADMIN_CREATED' AND DATE(al.created_at) = CURDATE()
)
LIMIT 2;

-- ============================================================================
-- SECTION 4: Verification
-- ============================================================================
-- Display all admin accounts that were created/updated
SELECT 
    id,
    email,
    role,
    first_name,
    last_name,
    is_active,
    created_at,
    updated_at
FROM users 
WHERE email IN (
    'main_sysadmin@reviewflow.com',
    'humberadmin@reviewflow.com',
    'yorkadmin@reviewflow.com'
)
ORDER BY role DESC, email ASC;

-- Count by role
SELECT role, COUNT(*) as count
FROM users 
WHERE role IN ('ADMIN', 'SYSTEM_ADMIN')
GROUP BY role;

-- ============================================================================
-- IMPORTANT NOTES
-- ============================================================================
-- 1. These accounts use placeholder bcrypt hashes ($2a$12$ChangeOnFirstLogin*)
--    Password MUST be reset on first login via API or backend password reset tool
--
-- 2. Roles created:
--    - SYSTEM_ADMIN: main_sysadmin@reviewflow.com (Platform operator)
--    - ADMIN:        humberadmin@reviewflow.com    (Academic admin)
--    - ADMIN:        yorkadmin@reviewflow.com      (Academic admin)
--
-- 3. Run this script ONLY ONCE - it uses ON DUPLICATE KEY UPDATE for safety
--
-- 4. Audit trail maintained in audit_log table for compliance
--
-- 5. If accounts already exist, script updates them to ensure correct roles
--
-- ============================================================================
