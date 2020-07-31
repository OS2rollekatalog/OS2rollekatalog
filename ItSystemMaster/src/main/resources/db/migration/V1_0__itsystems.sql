CREATE TABLE it_systems (
	id							BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    master_id                   VARCHAR(36) NOT NULL,
	name						VARCHAR(64) NOT NULL,
    last_modified               TIMESTAMP
);

CREATE TABLE system_roles (
	id							BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	name						VARCHAR(64) NOT NULL,
	identifier					VARCHAR(128) NOT NULL,
	description					TEXT,
	it_system_id				BIGINT NOT NULL,

	FOREIGN KEY (it_system_id) REFERENCES it_systems(id)
);
