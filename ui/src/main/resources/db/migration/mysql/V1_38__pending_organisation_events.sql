CREATE TABLE pending_organisation_updates (
	id							BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	entity_uuid                 VARCHAR(36) NOT NULL,
	event_type                  VARCHAR(64) NOT NULL,
	timestamp                   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);