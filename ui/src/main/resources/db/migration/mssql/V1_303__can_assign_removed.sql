
-- Step 1: Add columns to user_roles and rolegroup tables
ALTER TABLE user_roles
ADD requester_permission NVARCHAR(255) NOT NULL DEFAULT 'INHERIT',
    approver_permission NVARCHAR(255) NOT NULL DEFAULT 'INHERIT';
go

ALTER TABLE rolegroup
ADD requester_permission NVARCHAR(255) NOT NULL DEFAULT 'INHERIT',
    approver_permission NVARCHAR(255) NOT NULL DEFAULT 'INHERIT';
go

-- Step 2: Update rows based on can_request column
UPDATE user_roles
SET requester_permission = CASE WHEN can_request = 1 THEN 'MANAGERANDAUTHRESPONSIBLE' ELSE 'INHERIT' END,
    approver_permission = CASE WHEN can_request = 1 THEN 'ADMINONLY' ELSE 'INHERIT' END
WHERE can_request IS NOT NULL;

UPDATE rolegroup
SET requester_permission = CASE WHEN can_request = 1 THEN 'MANAGERANDAUTHRESPONSIBLE' ELSE 'INHERIT' END,
    approver_permission = CASE WHEN can_request = 1 THEN 'ADMINONLY' ELSE 'INHERIT' END
WHERE can_request IS NOT NULL;

-- Step 3: Drop the can_request column

DECLARE @constraint_name NVARCHAR(128);

-- Find and drop the default constraint for user_roles
SELECT @constraint_name = name
FROM sys.default_constraints
WHERE parent_object_id = OBJECT_ID('user_roles') AND col_name(parent_object_id, parent_column_id) = 'can_request';

IF @constraint_name IS NOT NULL
    EXEC('ALTER TABLE user_roles DROP CONSTRAINT ' + @constraint_name);

-- Find and drop the default constraint for rolegroup
SELECT @constraint_name = name
FROM sys.default_constraints
WHERE parent_object_id = OBJECT_ID('rolegroup') AND col_name(parent_object_id, parent_column_id) = 'can_request';

IF @constraint_name IS NOT NULL
    EXEC('ALTER TABLE rolegroup DROP CONSTRAINT ' + @constraint_name);

ALTER TABLE user_roles
DROP COLUMN can_request;

ALTER TABLE rolegroup
DROP COLUMN can_request;

