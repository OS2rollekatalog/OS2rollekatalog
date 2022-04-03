CREATE TABLE ou_title_mapping (
	id                            BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
	orgunit_uuid                  NVARCHAR(36) NOT NULL FOREIGN KEY REFERENCES ous(uuid) ON DELETE CASCADE,
	title_uuid                    NVARCHAR(36) NOT NULL FOREIGN KEY REFERENCES titles(uuid) ON DELETE CASCADE
);