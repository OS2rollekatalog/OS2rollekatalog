ALTER TABLE it_systems ADD attestation_responsible_uuid NVARCHAR(36) null;
ALTER TABLE it_systems ADD CONSTRAINT fk_it_systems_user FOREIGN KEY (attestation_responsible_uuid) REFERENCES users(uuid);
ALTER TABLE user_roles ADD role_assignment_attestation_by_attestation_responsible BIT NOT NULL DEFAULT 0;