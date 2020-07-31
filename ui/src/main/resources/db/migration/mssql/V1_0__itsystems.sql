CREATE TABLE it_systems (
	id							BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
	name						NVARCHAR(64) NOT NULL,
	identifier					NVARCHAR(64) NOT NULL
);

CREATE TABLE system_roles (
	id							BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
	name						NVARCHAR(64) NOT NULL,
	identifier					NVARCHAR(128) NOT NULL,
	description					NVARCHAR(MAX),
	it_system_id				BIGINT NOT NULL FOREIGN KEY REFERENCES it_systems(id)


);

CREATE TABLE system_role_constraint_types (
	system_role_id				BIGINT NOT NULL,
	constraint_type				NVARCHAR(64) NOT NULL
);