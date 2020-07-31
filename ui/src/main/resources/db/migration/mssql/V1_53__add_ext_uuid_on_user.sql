ALTER TABLE users ADD ext_uuid NVARCHAR(36);
GO
UPDATE users SET ext_uuid = uuid;
ALTER TABLE users ALTER COLUMN ext_uuid NVARCHAR(36) NOT NULL;