CREATE TABLE ou_authorization_managers (
	id							BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	ou_uuid						VARCHAR(36) NOT NULL,
	user_uuid					VARCHAR(36) NOT NULL,
	
	FOREIGN KEY (ou_uuid) REFERENCES ous(uuid) ON DELETE CASCADE,
	FOREIGN KEY (user_uuid) REFERENCES users(uuid) ON DELETE CASCADE
);
