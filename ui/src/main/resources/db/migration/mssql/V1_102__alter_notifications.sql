ALTER TABLE notifications ALTER COLUMN affected_entity_uuid NVARCHAR(36) NULL;
ALTER TABLE notifications ALTER COLUMN affected_entity_type NVARCHAR(255) NULL;
ALTER TABLE notifications ALTER COLUMN affected_entity_name NVARCHAR(255) NULL;