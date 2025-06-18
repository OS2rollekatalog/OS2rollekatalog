ALTER TABLE it_systems DROP FOREIGN KEY fk_it_systems_user;
ALTER TABLE it_systems ADD CONSTRAINT fk_it_systems_user FOREIGN KEY (attestation_responsible_uuid) REFERENCES users(uuid) ON DELETE SET NULL;
