CREATE TABLE attestation_notifications (
	id					BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	ou_uuid				varchar(36) NOT NULL,
	tts					TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	
	FOREIGN KEY (ou_uuid) REFERENCES ous(uuid) ON DELETE CASCADE
);