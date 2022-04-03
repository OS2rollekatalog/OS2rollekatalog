CREATE TABLE ou_title_mapping (
	id                            BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	orgunit_uuid                  VARCHAR(36) NOT NULL,
	title_uuid                    VARCHAR(36) NOT NULL,

	FOREIGN KEY (orgunit_uuid) REFERENCES ous(uuid) ON DELETE CASCADE,
	FOREIGN KEY (title_uuid) REFERENCES titles(uuid) ON DELETE CASCADE
);