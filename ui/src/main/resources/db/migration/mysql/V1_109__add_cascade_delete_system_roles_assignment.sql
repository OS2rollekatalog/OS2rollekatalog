SELECT CONCAT(
 'ALTER TABLE `system_role_assignments` DROP FOREIGN KEY `',
 constraint_name,
 '`'
) INTO @sqlst
 FROM information_schema.KEY_COLUMN_USAGE
 WHERE table_name = 'system_role_assignments'
  AND referenced_table_name='user_roles'
  AND referenced_column_name='id' LIMIT 1;

SELECT @sqlst;

PREPARE stmt FROM @sqlst;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sqlst = NULL;

ALTER TABLE system_role_assignments
 ADD CONSTRAINT
 FOREIGN KEY (user_role_id)
 REFERENCES user_roles(id)
 ON DELETE CASCADE;