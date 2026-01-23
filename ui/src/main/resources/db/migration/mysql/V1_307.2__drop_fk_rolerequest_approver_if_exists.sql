SET @exists := (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'req_role_request'
      AND CONSTRAINT_NAME = 'fk_rolerequest_approver'
);

SET @sql := IF(@exists > 0,
               'ALTER TABLE req_role_request DROP FOREIGN KEY fk_rolerequest_approver',
               'SELECT 1'
            );

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;