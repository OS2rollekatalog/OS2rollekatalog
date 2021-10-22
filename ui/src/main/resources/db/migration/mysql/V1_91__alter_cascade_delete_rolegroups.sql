SELECT CONCAT('ALTER TABLE `rolegroup_roles` DROP FOREIGN KEY `', constraint_name, '`')
  INTO @sqlst
  FROM information_schema.KEY_COLUMN_USAGE
    WHERE table_name = 'rolegroup_roles'
      AND referenced_table_name='user_roles'
      AND referenced_column_name='id' LIMIT 1;

SELECT @sqlst;

PREPARE stmt FROM @sqlst;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @sqlst = NULL;

ALTER TABLE rolegroup_roles
	ADD CONSTRAINT
	FOREIGN KEY (role_id)
	REFERENCES user_roles(id)
	ON DELETE CASCADE;
