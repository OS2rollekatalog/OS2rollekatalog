-- identify and remove old constraint with missing ON DELETE CASCADE
SET @fk_name = NULL;

SELECT CONSTRAINT_NAME INTO @fk_name
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'user_rolegroups'
  AND COLUMN_NAME = 'rolegroup_id'
  AND REFERENCED_TABLE_NAME = 'rolegroup'
LIMIT 1;

SET @sql = IF(@fk_name IS NOT NULL,
    CONCAT('ALTER TABLE user_rolegroups DROP FOREIGN KEY `', @fk_name, '`'),
    'SELECT 1');   -- harmless no-op if nothing is found
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- add new (named) constraint with ON DELETE CASCADE
ALTER TABLE user_rolegroups
  ADD CONSTRAINT fk_user_rolegroups_rolegroup
    FOREIGN KEY (rolegroup_id) REFERENCES rolegroup (id) ON DELETE CASCADE;

-- identify and remove old constraint with missing ON DELETE CASCADE
SET @fk_name = NULL;

SELECT CONSTRAINT_NAME INTO @fk_name
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'user_rolegroups'
  AND COLUMN_NAME = 'user_uuid'
  AND REFERENCED_TABLE_NAME = 'users'
LIMIT 1;

SET @sql = IF(@fk_name IS NOT NULL,
    CONCAT('ALTER TABLE user_rolegroups DROP FOREIGN KEY `', @fk_name, '`'),
    'SELECT 1');   -- harmless no-op if nothing is found
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- add new (named) constraint with ON DELETE CASCADE
ALTER TABLE user_rolegroups
  ADD CONSTRAINT fk_user_rolegroups_users
    FOREIGN KEY (user_uuid) REFERENCES users (uuid) ON DELETE CASCADE;

-- remove old constraint with ON DELETE CASCADE and recreate with ON DELETE SET NULL
ALTER TABLE user_rolegroups DROP FOREIGN KEY fk_user_rolegroups_ou;
ALTER TABLE user_rolegroups ADD CONSTRAINT fk_user_rolegroups_ou FOREIGN KEY (ou_uuid) REFERENCES ous (uuid) ON DELETE SET NULL;


-- identify and remove old constraint with missing ON DELETE CASCADE
SET @fk_name = NULL;

SELECT CONSTRAINT_NAME INTO @fk_name
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'ou_rolegroups'
  AND COLUMN_NAME = 'rolegroup_id'
  AND REFERENCED_TABLE_NAME = 'rolegroup'
LIMIT 1;

SET @sql = IF(@fk_name IS NOT NULL,
    CONCAT('ALTER TABLE ou_rolegroups DROP FOREIGN KEY `', @fk_name, '`'),
    'SELECT 1');   -- harmless no-op if nothing is found
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- add new (named) constraint with ON DELETE CASCADE
ALTER TABLE ou_rolegroups
  ADD CONSTRAINT fk_ou_rolegroups_rolegroup
    FOREIGN KEY (rolegroup_id) REFERENCES rolegroup (id) ON DELETE CASCADE;

-- identify and remove old constraint with missing ON DELETE CASCADE
SET @fk_name = NULL;

SELECT CONSTRAINT_NAME INTO @fk_name
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'ou_rolegroups'
  AND COLUMN_NAME = 'ou_uuid'
  AND REFERENCED_TABLE_NAME = 'ous'
LIMIT 1;

SET @sql = IF(@fk_name IS NOT NULL,
    CONCAT('ALTER TABLE ou_rolegroups DROP FOREIGN KEY `', @fk_name, '`'),
    'SELECT 1');   -- harmless no-op if nothing is found
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- add new (named) constraint with ON DELETE CASCADE
ALTER TABLE ou_rolegroups
  ADD CONSTRAINT fk_ou_rolegroups_ous
    FOREIGN KEY (ou_uuid) REFERENCES ous (uuid) ON DELETE CASCADE;

-- identify and remove old constraint with missing ON DELETE CASCADE
SET @fk_name = NULL;

SELECT CONSTRAINT_NAME INTO @fk_name
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'ou_roles'
  AND COLUMN_NAME = 'ou_uuid'
  AND REFERENCED_TABLE_NAME = 'ous'
LIMIT 1;

SET @sql = IF(@fk_name IS NOT NULL,
    CONCAT('ALTER TABLE ou_roles DROP FOREIGN KEY `', @fk_name, '`'),
    'SELECT 1');   -- harmless no-op if nothing is found
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- add new (named) constraint with ON DELETE CASCADE
ALTER TABLE ou_roles
  ADD CONSTRAINT fk_ou_roles_ous
    FOREIGN KEY (ou_uuid) REFERENCES ous (uuid) ON DELETE CASCADE;

-- identify and remove old constraint with missing ON DELETE CASCADE
SET @fk_name = NULL;

SELECT CONSTRAINT_NAME INTO @fk_name
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'user_roles_mapping'
  AND COLUMN_NAME = 'user_uuid'
  AND REFERENCED_TABLE_NAME = 'users'
LIMIT 1;

SET @sql = IF(@fk_name IS NOT NULL,
    CONCAT('ALTER TABLE user_roles_mapping DROP FOREIGN KEY `', @fk_name, '`'),
    'SELECT 1');   -- harmless no-op if nothing is found
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- add new (named) constraint with ON DELETE CASCADE
ALTER TABLE user_roles_mapping
  ADD CONSTRAINT fk_user_roles_mapping_users
    FOREIGN KEY (user_uuid) REFERENCES users (uuid) ON DELETE CASCADE;

-- remove old constraint with ON DELETE CASCADE and recreate with ON DELETE SET NULL
ALTER TABLE user_roles_mapping DROP FOREIGN KEY fk_user_roles_mapping_ou;
ALTER TABLE user_roles_mapping ADD CONSTRAINT fk_user_roles_mapping_ou FOREIGN KEY (ou_uuid) REFERENCES ous (uuid) ON DELETE SET NULL;
