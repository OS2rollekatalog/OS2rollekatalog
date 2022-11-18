-- update deleted field with reverse value of old active field
UPDATE system_roles SET weight = 1 WHERE weight = 0;