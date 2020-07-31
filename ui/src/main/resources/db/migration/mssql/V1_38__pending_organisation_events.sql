CREATE TABLE pending_organisation_updates (
	id							BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
	entity_uuid                 NVARCHAR(36) NOT NULL,
	event_type                  NVARCHAR(64) NOT NULL,
	timestamp                   DATETIME2 NOT NULL DEFAULT GETDATE()
);