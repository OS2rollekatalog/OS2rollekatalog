CREATE TABLE it_systems (
	id							BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	name						VARCHAR(64) NOT NULL,
	identifier					VARCHAR(64) NOT NULL
);

CREATE TABLE system_roles (
	id							BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	name						VARCHAR(64) NOT NULL,
	identifier					VARCHAR(128) NOT NULL,
	description					TEXT,
	it_system_id				BIGINT NOT NULL,

	FOREIGN KEY (it_system_id) REFERENCES it_systems(id)
);

CREATE TABLE system_role_constraint_types (
	system_role_id				BIGINT NOT NULL,
	constraint_type				VARCHAR(64) NOT NULL
);