CREATE TABLE attestations (
    id                          BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
	user_uuid                   NVARCHAR(36) NOT NULL FOREIGN KEY REFERENCES users(uuid) ON DELETE CASCADE,
	orgunit_uuid                NVARCHAR(36) NOT NULL FOREIGN KEY REFERENCES ous(uuid) ON DELETE CASCADE,
	notified                    BIT NOT NULL DEFAULT 0,
	created						DATETIME2 NOT NULL DEFAULT GETDATE()
);

-- constraints prohibits dropping these columns, cleanup must be manually performed

--ALTER TABLE users DROP COLUMN attestation_deadline;
--ALTER TABLE users DROP COLUMN attestation_helpdesk_notified;
--ALTER TABLE users DROP COLUMN attestation_manager_notified;
--ALTER TABLE positions DROP COLUMN attestation_deadline;
--ALTER TABLE positions DROP COLUMN attestation_helpdesk_notified;
--ALTER TABLE positions DROP COLUMN attestation_manager_notified;
--ALTER TABLE pending_organisation_updates DROP COLUMN entity_uuid;

ALTER TABLE pending_organisation_updates ADD orgunit_uuid NVARCHAR(36);
ALTER TABLE pending_organisation_updates ADD user_uuid NVARCHAR(36);