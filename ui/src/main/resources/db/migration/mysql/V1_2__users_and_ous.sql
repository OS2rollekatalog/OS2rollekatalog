CREATE TABLE users (
	uuid							VARCHAR(36) PRIMARY KEY,
	user_id							VARCHAR(64),
	name							VARCHAR(128),
	active							TINYINT(1)
);

CREATE TABLE ous (
	uuid							VARCHAR(36) PRIMARY KEY,
	name							VARCHAR(64),
	parent_uuid						VARCHAR(36),
	active							TINYINT(1)
);

CREATE TABLE positions (
    id								BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	user_uuid						VARCHAR(36) NOT NULL,
	ou_uuid							VARCHAR(36) NOT NULL,
	name							VARCHAR(64) NOT NULL,

	FOREIGN KEY (user_uuid) REFERENCES users(uuid) ON DELETE CASCADE,
	FOREIGN KEY (ou_uuid) REFERENCES ous(uuid) ON DELETE CASCADE
);

CREATE TABLE ou_kles (
	id								BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	ou_uuid							VARCHAR(36) NOT NULL,
	code							VARCHAR(8),
	assignment_type					VARCHAR(16),

	FOREIGN KEY (ou_uuid) REFERENCES ous(uuid) ON DELETE CASCADE
);

CREATE TABLE ou_roles (
	ou_uuid							VARCHAR(36) NOT NULL,
	role_id							BIGINT NOT NULL,

	FOREIGN KEY (ou_uuid) REFERENCES ous(uuid),
	FOREIGN KEY (role_id) REFERENCES user_roles(id)
);

CREATE TABLE ou_rolegroups (
	ou_uuid							VARCHAR(36) NOT NULL,
	rolegroup_id					BIGINT NOT NULL,

	FOREIGN KEY (ou_uuid) REFERENCES ous(uuid),
	FOREIGN KEY (rolegroup_id) REFERENCES rolegroup(id)
);

-- bad name for the table, but this is because it would clash with user_roles table otherwise :(
CREATE TABLE user_roles_mapping (
	user_uuid						VARCHAR(36) NOT NULL,
	role_id							BIGINT NOT NULL,

	FOREIGN KEY (user_uuid) REFERENCES users(uuid),
	FOREIGN KEY (role_id) REFERENCES user_roles(id)
);

CREATE TABLE user_rolegroups (
	user_uuid						VARCHAR(36) NOT NULL,
	rolegroup_id					BIGINT NOT NULL,

	FOREIGN KEY (user_uuid) REFERENCES users(uuid),
	FOREIGN KEY (rolegroup_id) REFERENCES rolegroup(id)
);

CREATE TABLE position_roles (
	position_id					    BIGINT NOT NULL,
	role_id							BIGINT NOT NULL,

	FOREIGN KEY (position_id) REFERENCES positions(id) ON DELETE CASCADE,
	FOREIGN KEY (role_id) REFERENCES user_roles(id) ON DELETE CASCADE
);

CREATE TABLE position_rolegroups (
	position_id						BIGINT NOT NULL,
	rolegroup_id					BIGINT NOT NULL,

	FOREIGN KEY (position_id) REFERENCES positions(id) ON DELETE CASCADE,
	FOREIGN KEY (rolegroup_id) REFERENCES rolegroup(id) ON DELETE CASCADE
);
