ALTER TABLE it_systems ADD COLUMN attestation_responsible_uuid VARCHAR(36) null;
ALTER TABLE it_systems ADD CONSTRAINT fk_it_systems_user FOREIGN KEY (attestation_responsible_uuid) REFERENCES users(uuid);
ALTER TABLE user_roles ADD COLUMN role_assignment_attestation_by_attestation_responsible BOOL NOT NULL DEFAULT FALSE;