-- Match the ON DELETE SET NULL behaviour of fk_it_systems_user (attestation_responsible_uuid,
-- set in V1_195). Without this, OrganisationImporter's physical delete of a stale user fails
-- with a foreign key violation when the user is still set as systemOwner on an IT system.
ALTER TABLE it_systems DROP FOREIGN KEY fk_it_systems_user_system;
ALTER TABLE it_systems ADD CONSTRAINT fk_it_systems_user_system FOREIGN KEY (system_owner_uuid) REFERENCES users(uuid) ON DELETE SET NULL;
