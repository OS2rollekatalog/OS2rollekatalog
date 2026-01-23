SET @sql = (
 SELECT CONCAT('ALTER TABLE system_role_supported_constraints DROP FOREIGN KEY ', CONSTRAINT_NAME)
 FROM information_schema.KEY_COLUMN_USAGE 
 WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'system_role_supported_constraints' 
  AND COLUMN_NAME = 'system_role_id'
  AND REFERENCED_TABLE_NAME IS NOT NULL
 LIMIT 1
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE system_role_supported_constraints 
 ADD CONSTRAINT fk_srsc_system_role_id
 FOREIGN KEY (system_role_id) REFERENCES system_roles(id) 
 ON DELETE CASCADE;
