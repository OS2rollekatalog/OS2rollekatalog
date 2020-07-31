ALTER TABLE audit_log ADD COLUMN entity_name VARCHAR(512) AFTER entity_id;
ALTER TABLE audit_log ADD COLUMN secondary_entity_name VARCHAR(512) AFTER secondary_entity_id;
ALTER TABLE audit_log ADD COLUMN description TEXT;