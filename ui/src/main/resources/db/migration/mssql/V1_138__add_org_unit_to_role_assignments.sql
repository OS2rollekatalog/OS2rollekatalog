-- TODO tag stilling til om ON DELETE CASCADE skal være der eller slettes
ALTER TABLE user_rolegroups ADD ou_uuid NVARCHAR(36) null;
ALTER TABLE user_rolegroups ADD CONSTRAINT fk_user_rolegroups_ou FOREIGN KEY (ou_uuid) REFERENCES ous(uuid) ON DELETE CASCADE;
ALTER TABLE user_roles_mapping ADD ou_uuid NVARCHAR(36) null;
ALTER TABLE user_roles_mapping ADD CONSTRAINT fk_user_roles_mapping_ou FOREIGN KEY (ou_uuid) REFERENCES ous(uuid) ON DELETE CASCADE;