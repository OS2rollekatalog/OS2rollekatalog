CREATE TABLE pending_ad_group_operations (
	id							BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    timestamp                   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
	system_role_id              BIGINT NULL,
	system_role_identifier      VARCHAR(255) NOT NULL,
	it_system_identifier        VARCHAR(255) NOT NULL,
	active                      BIT NOT NULL DEFAULT 1,

	FOREIGN KEY (system_role_id) REFERENCES system_roles(id) ON DELETE CASCADE
);