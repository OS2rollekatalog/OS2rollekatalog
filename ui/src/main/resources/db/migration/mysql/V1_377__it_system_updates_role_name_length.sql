-- V1_186 widened system_roles.name to 255 but missed this copy of it.
-- 128 -> 255 keeps the same VARCHAR length prefix, so INSTANT holds.
ALTER TABLE it_system_updates
    MODIFY COLUMN system_role_name VARCHAR(255) NULL, ALGORITHM=INSTANT;
