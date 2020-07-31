ALTER TABLE users ADD ext_uuid VARCHAR(36);
UPDATE users SET ext_uuid = uuid;
ALTER TABLE users MODIFY COLUMN ext_uuid VARCHAR(36) NOT NULL;