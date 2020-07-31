CREATE TABLE user_roles (
	id							BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	name						VARCHAR(64) NOT NULL,
	identifier					VARCHAR(128) NOT NULL,
	description					TEXT,
	it_system_id				BIGINT NOT NULL,

	FOREIGN KEY (it_system_id) REFERENCES it_systems(id)
);

CREATE TABLE system_role_assignments (
	id							BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	user_role_id				BIGINT NOT NULL,
	system_role_id				BIGINT NOT NULL,

	FOREIGN KEY (user_role_id) REFERENCES user_roles(id),
	FOREIGN KEY (system_role_id) REFERENCES system_roles(id)
);

CREATE TABLE system_role_assignment_constraint_values (
	id							BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	constraint_type				VARCHAR(64) NOT NULL,
	constraint_value_type		VARCHAR(64) NOT NULL,
	constraint_value			VARCHAR(1024),
	system_role_assignment_id	BIGINT NOT NULL,

	FOREIGN KEY (system_role_assignment_id) REFERENCES system_role_assignments(id)
);

CREATE TABLE rolegroup (
	id							BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	name						VARCHAR(64) UNIQUE,
	description					TEXT
);

CREATE TABLE rolegroup_roles (
	rolegroup_id				BIGINT NOT NULL,
	role_id						BIGINT NOT NULL,

	FOREIGN KEY (rolegroup_id) REFERENCES rolegroup(id) ON DELETE CASCADE,
	FOREIGN KEY (role_id) REFERENCES user_roles(id)
);