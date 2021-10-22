CREATE TABLE ou_authorization_managers (
	id							BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
	ou_uuid						NVARCHAR(36) NOT NULL,
	user_uuid					NVARCHAR(36) NOT NULL,
	
	FOREIGN KEY (ou_uuid) REFERENCES ous(uuid) ON DELETE CASCADE,
	FOREIGN KEY (user_uuid) REFERENCES users(uuid) ON DELETE CASCADE
);
