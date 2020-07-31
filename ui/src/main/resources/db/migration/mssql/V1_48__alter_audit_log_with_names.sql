ALTER TABLE audit_log ADD entity_name NVARCHAR(512);
ALTER TABLE audit_log ADD secondary_entity_name NVARCHAR(512);
ALTER TABLE audit_log ADD description NTEXT;