CREATE TABLE pending_ad_group_operations (
    id                          BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
    timestamp                   DATETIME2 NOT NULL DEFAULT GETDATE(),
	system_role_id              BIGINT NULL FOREIGN KEY REFERENCES system_roles(id) ON DELETE CASCADE
	system_role_identifier      NVARCHAR(255) NOT NULL,
	it_system_identifier        NVARCHAR(255) NOT NULL,
	active                      BIT NOT NULL DEFAULT 1
);