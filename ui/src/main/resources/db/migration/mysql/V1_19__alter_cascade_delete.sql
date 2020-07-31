SELECT CONCAT(
	'ALTER TABLE `system_role_assignments` DROP FOREIGN KEY `',
	constraint_name,
	'`'
) INTO @sqlst
	FROM information_schema.KEY_COLUMN_USAGE
	WHERE table_name = 'system_role_assignments'
		AND referenced_table_name='system_roles'
		AND referenced_column_name='id' LIMIT 1;

SELECT @sqlst;

PREPARE stmt FROM @sqlst;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sqlst = NULL;

ALTER TABLE system_role_assignments
	ADD CONSTRAINT
	FOREIGN KEY (system_role_id)
	REFERENCES system_roles(id)
	ON DELETE CASCADE;

SELECT CONCAT(
	'ALTER TABLE `system_role_assignment_constraint_values` DROP FOREIGN KEY `',
	constraint_name,
	'`'
) INTO @sqlst2
	FROM information_schema.KEY_COLUMN_USAGE
	WHERE table_name = 'system_role_assignment_constraint_values'
		AND referenced_table_name='system_role_assignments'
		AND referenced_column_name='id' LIMIT 1;

SELECT @sqlst2;

PREPARE stmt2 FROM @sqlst2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
SET @sqlst2 = NULL;

ALTER TABLE system_role_assignment_constraint_values
	ADD CONSTRAINT
	FOREIGN KEY (system_role_assignment_id)
	REFERENCES system_role_assignments(id)
	ON DELETE CASCADE;
	
SELECT CONCAT(
	'ALTER TABLE `ou_roles` DROP FOREIGN KEY `',
	constraint_name,
	'`'
) INTO @sqlst
	FROM information_schema.KEY_COLUMN_USAGE
	WHERE table_name = 'ou_roles'
		AND referenced_table_name='user_roles'
		AND referenced_column_name='id' LIMIT 1;

SELECT @sqlst;

PREPARE stmt FROM @sqlst;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sqlst = NULL;

ALTER TABLE ou_roles
	ADD CONSTRAINT
	FOREIGN KEY (role_id)
	REFERENCES user_roles(id)
	ON DELETE CASCADE;

SELECT CONCAT(
	'ALTER TABLE `user_roles_mapping` DROP FOREIGN KEY `',
	constraint_name,
	'`'
) INTO @sqlst
	FROM information_schema.KEY_COLUMN_USAGE
	WHERE table_name = 'user_roles_mapping'
		AND referenced_table_name='user_roles'
		AND referenced_column_name='id' LIMIT 1;

SELECT @sqlst;

PREPARE stmt FROM @sqlst;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sqlst = NULL;

ALTER TABLE user_roles_mapping
	ADD CONSTRAINT
	FOREIGN KEY (role_id)
	REFERENCES user_roles(id)
	ON DELETE CASCADE;
