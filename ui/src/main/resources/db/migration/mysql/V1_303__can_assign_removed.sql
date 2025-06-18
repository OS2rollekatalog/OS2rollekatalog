

START TRANSACTION;

-- Step 1: Add columns to user_roles and rolegroup tables
ALTER TABLE user_roles
ADD COLUMN requester_permission VARCHAR(255) NOT NULL DEFAULT 'INHERIT',
    ADD COLUMN approver_permission VARCHAR(255) NOT NULL DEFAULT 'INHERIT';

ALTER TABLE rolegroup
ADD COLUMN requester_permission VARCHAR(255) NOT NULL DEFAULT 'INHERIT',
    ADD COLUMN approver_permission VARCHAR(255) NOT NULL DEFAULT 'INHERIT';

-- Step 2: Update rows based on can_request column
UPDATE user_roles
SET requester_permission = CASE WHEN can_request = 1 THEN 'MANAGERANDAUTHRESPONSIBLE' ELSE 'INHERIT' END,
    approver_permission = CASE WHEN can_request = 1 THEN 'ADMINONLY' ELSE 'INHERIT' END;

UPDATE rolegroup
SET requester_permission = CASE WHEN can_request = 1 THEN 'MANAGERANDAUTHRESPONSIBLE' ELSE 'INHERIT' END,
    approver_permission = CASE WHEN can_request = 1 THEN 'ADMINONLY' ELSE 'INHERIT' END;

-- Step 3: Drop the can_request column
ALTER TABLE user_roles
DROP COLUMN can_request;

ALTER TABLE rolegroup
DROP COLUMN can_request;

COMMIT;
