SET SESSION lock_wait_timeout = 10;

-- we need VARCHAR(256) to match the other table, but current size is VARCHAR(64), and we cannot increase above 85 without doing a full rewrite of the table
-- and this table is huge, so that is a no-go for now

DELIMITER //
BEGIN NOT ATOMIC
  DECLARE cur_len INT;

  SELECT CHARACTER_MAXIMUM_LENGTH
    INTO cur_len
    FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME   = 'history_ou_role_assignments'
     AND COLUMN_NAME  = 'role_it_system_name';

  IF cur_len IS NOT NULL AND cur_len <> 256 THEN
    ALTER TABLE history_ou_role_assignments
      MODIFY COLUMN role_it_system_name VARCHAR(85) NOT NULL, ALGORITHM=INSTANT;
  END IF;
END //
DELIMITER ;