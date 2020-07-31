CREATE TABLE attestations (
	id							BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	user_uuid                   VARCHAR(36) NOT NULL,
	orgunit_uuid                VARCHAR(36) NOT NULL,
	notified                    BOOLEAN NOT NULL DEFAULT 0,
	created						TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	
	FOREIGN KEY (user_uuid) REFERENCES users(uuid) ON DELETE CASCADE,
	FOREIGN KEY (orgunit_uuid) REFERENCES ous(uuid) ON DELETE CASCADE
);

ALTER TABLE users DROP COLUMN attestation_deadline;
ALTER TABLE users DROP COLUMN attestation_helpdesk_notified;
ALTER TABLE users DROP COLUMN attestation_manager_notified;

ALTER TABLE positions DROP COLUMN attestation_deadline;
ALTER TABLE positions DROP COLUMN attestation_helpdesk_notified;
ALTER TABLE positions DROP COLUMN attestation_manager_notified;

ALTER TABLE pending_organisation_updates DROP COLUMN entity_uuid;
ALTER TABLE pending_organisation_updates ADD COLUMN orgunit_uuid VARCHAR(36);
ALTER TABLE pending_organisation_updates ADD COLUMN user_uuid VARCHAR(36);