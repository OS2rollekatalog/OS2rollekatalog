CREATE TABLE users (
	uuid							NVARCHAR(36) PRIMARY KEY,
	user_id							NVARCHAR(64),
	name							NVARCHAR(128),
	active							SMALLINT
);

CREATE TABLE ous (
	uuid							NVARCHAR(36) PRIMARY KEY,
	name							NVARCHAR(64),
	parent_uuid						NVARCHAR(36),
	active							SMALLINT
);

CREATE TABLE positions (
    id								BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
	user_uuid						NVARCHAR(36) NOT NULL FOREIGN KEY REFERENCES users(uuid) ON DELETE CASCADE,
	ou_uuid							NVARCHAR(36) NOT NULL FOREIGN KEY REFERENCES ous(uuid) ON DELETE CASCADE,
	name							NVARCHAR(64) NOT NULL
);

CREATE TABLE ou_kles (
	id								BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
	ou_uuid							NVARCHAR(36) NOT NULL FOREIGN KEY REFERENCES ous(uuid) ON DELETE CASCADE,
	code							NVARCHAR(8),
	assignment_type					NVARCHAR(16)
);

CREATE TABLE ou_roles (
	ou_uuid							NVARCHAR(36) NOT NULL FOREIGN KEY REFERENCES ous(uuid),
	role_id							BIGINT NOT NULL FOREIGN KEY REFERENCES user_roles(id)
);

CREATE TABLE ou_rolegroups (
	ou_uuid							NVARCHAR(36) NOT NULL FOREIGN KEY REFERENCES ous(uuid),
	rolegroup_id					BIGINT NOT NULL FOREIGN KEY REFERENCES rolegroup(id)
);

-- bad name for the table, but this is because it would clash with user_roles table otherwise :(
CREATE TABLE user_roles_mapping (
	user_uuid						NVARCHAR(36) NOT NULL FOREIGN KEY REFERENCES users(uuid),
	role_id							BIGINT NOT NULL FOREIGN KEY REFERENCES user_roles(id)
);

CREATE TABLE user_rolegroups (
	user_uuid						NVARCHAR(36) NOT NULL FOREIGN KEY REFERENCES users(uuid),
	rolegroup_id					BIGINT NOT NULL FOREIGN KEY REFERENCES rolegroup(id)
);

CREATE TABLE position_roles (
	position_id					    BIGINT NOT NULL FOREIGN KEY REFERENCES positions(id) ON DELETE CASCADE,
	role_id							BIGINT NOT NULL FOREIGN KEY REFERENCES user_roles(id) ON DELETE CASCADE
);

CREATE TABLE position_rolegroups (
	position_id						BIGINT NOT NULL FOREIGN KEY REFERENCES positions(id) ON DELETE CASCADE,
	rolegroup_id					BIGINT NOT NULL FOREIGN KEY REFERENCES rolegroup(id) ON DELETE CASCADE
);
