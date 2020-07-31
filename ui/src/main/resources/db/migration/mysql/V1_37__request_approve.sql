CREATE TABLE request_approve (
	id							BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	requester_uuid              VARCHAR(36) NOT NULL,
	manager_uuid                VARCHAR(36),
	assigner_uuid               VARCHAR(36),
	role_type                   VARCHAR(64) NOT NULL,
	role_id                     BIGINT NOT NULL,
	reason                      TEXT,
	reject_reason               TEXT,
	role_assigner_notified      BOOLEAN NOT NULL DEFAULT 0,
	status                      VARCHAR(64) NOT NULL,
	request_timestamp           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	status_timestamp            TIMESTAMP NULL,
	
	FOREIGN KEY (requester_uuid) REFERENCES users(uuid) ON DELETE CASCADE,
	FOREIGN KEY (manager_uuid) REFERENCES users(uuid) ON DELETE CASCADE,
	FOREIGN KEY (assigner_uuid) REFERENCES users(uuid) ON DELETE CASCADE
);