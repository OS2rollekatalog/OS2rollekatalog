ALTER TABLE notifications MODIFY affected_entity_uuid VARCHAR(36) NULL;
ALTER TABLE notifications MODIFY affected_entity_type VARCHAR(255) NULL;
ALTER TABLE notifications MODIFY affected_entity_name VARCHAR(255) NULL;