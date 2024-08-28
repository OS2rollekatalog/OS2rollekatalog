ALTER TABLE it_systems ADD system_owner_uuid NVARCHAR(36) null;
ALTER TABLE it_systems ADD CONSTRAINT fk_it_systems_user_system FOREIGN KEY (system_owner_uuid) REFERENCES users(uuid);
ALTER TABLE history_it_systems ADD system_owner_uuid NVARCHAR(36) DEFAULT NULL;