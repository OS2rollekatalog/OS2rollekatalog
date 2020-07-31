CREATE TABLE user_roles (
	id							BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
	name						NVARCHAR(64) NOT NULL,
	identifier					NVARCHAR(128) NOT NULL,
	description					NVARCHAR(MAX),
	it_system_id				BIGINT NOT NULL FOREIGN KEY REFERENCES it_systems(id)
);

CREATE TABLE system_role_assignments (
	id							BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
	user_role_id				BIGINT NOT NULL FOREIGN KEY REFERENCES  user_roles(id),
	system_role_id				BIGINT NOT NULL FOREIGN KEY REFERENCES system_roles(id)
);

CREATE TABLE system_role_assignment_constraint_values (
	id							BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
	constraint_type				NVARCHAR(64) NOT NULL,
	constraint_value_type		NVARCHAR(64) NOT NULL,
	constraint_value			NVARCHAR(1024),
	system_role_assignment_id	BIGINT NOT NULL FOREIGN KEY REFERENCES system_role_assignments(id)
);

CREATE TABLE rolegroup (
	id							BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
	name						NVARCHAR(64) UNIQUE,
	description					NVARCHAR(MAX)
);

CREATE TABLE rolegroup_roles (
	rolegroup_id				BIGINT NOT NULL FOREIGN KEY REFERENCES rolegroup(id) ON DELETE CASCADE,
	role_id						BIGINT NOT NULL FOREIGN KEY REFERENCES user_roles(id)
);