CREATE TABLE ou_rolegroups_excepted_users (
    id                      BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    ou_rolegroups_id        BIGINT NOT NULL,
    user_uuid               VARCHAR(36) NOT NULL,

	FOREIGN KEY (user_uuid) REFERENCES users(uuid) ON DELETE CASCADE,
	FOREIGN KEY (ou_rolegroups_id) REFERENCES ou_rolegroups(id) ON DELETE CASCADE
);

CREATE TABLE ou_roles_excepted_users (
    id                      BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    ou_roles_id             BIGINT NOT NULL,
    user_uuid               VARCHAR(36) NOT NULL,

	FOREIGN KEY (user_uuid) REFERENCES users(uuid) ON DELETE CASCADE,
	FOREIGN KEY (ou_roles_id) REFERENCES ou_roles(id) ON DELETE CASCADE
);

ALTER TABLE ou_roles ADD COLUMN contains_excepted_users BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE ou_rolegroups ADD COLUMN contains_excepted_users BOOLEAN NOT NULL DEFAULT 0;

-- History tables for exceptions
CREATE TABLE history_role_assignment_excepted_users (
  id                        BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,

  -- date for entry
  dato                      DATE NOT NULL,

  -- ou reference
  ou_uuid                   VARCHAR(36) NOT NULL,

  -- user reference
  user_uuids                TEXT NOT NULL,

  -- role
  role_id                   BIGINT NOT NULL,
  role_name                 VARCHAR(64) NOT NULL,
  role_it_system_id         BIGINT NOT NULL,
  role_it_system_name       VARCHAR(64) NOT NULL,
  role_role_group           VARCHAR(64),

  -- assigned by, and when
  assigned_by_user_id       VARCHAR(255) NOT NULL,
  assigned_by_name          VARCHAR(255) NOT NULL,
  assigned_when             DATETIME NOT NULL,

  INDEX(dato)
);