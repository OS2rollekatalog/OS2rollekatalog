CREATE TABLE ou_rolegroups_excepted_users (
    id                      BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
    ou_rolegroups_id        BIGINT NOT NULL FOREIGN KEY REFERENCES ou_rolegroups(id) ON DELETE CASCADE,
    user_uuid               NVARCHAR(36) NOT NULL FOREIGN KEY REFERENCES users(uuid) ON DELETE CASCADE
);

CREATE TABLE ou_roles_excepted_users (
    id                      BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
    ou_roles_id             BIGINT NOT NULL FOREIGN KEY REFERENCES ou_roles(id) ON DELETE CASCADE,
    user_uuid               NVARCHAR(36) NOT NULL FOREIGN KEY REFERENCES users(uuid) ON DELETE CASCADE
);

ALTER TABLE ou_roles ADD contains_excepted_users BIT NOT NULL DEFAULT 0;
ALTER TABLE ou_rolegroups ADD contains_excepted_users BIT NOT NULL DEFAULT 0;

-- History tables for exceptions
CREATE TABLE history_role_assignment_excepted_users (
  id                        BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
  dato                      DATE NOT NULL,
  ou_uuid                   NVARCHAR(36) NOT NULL,
  user_uuids                NTEXT NOT NULL,
  role_id                   BIGINT NOT NULL,
  role_name                 NVARCHAR(64) NOT NULL,
  role_it_system_id         BIGINT NOT NULL,
  role_it_system_name       NVARCHAR(64) NOT NULL,
  role_role_group           NVARCHAR(64),
  assigned_by_user_id       NVARCHAR(255) NOT NULL,
  assigned_by_name          NVARCHAR(255) NOT NULL,
  assigned_when             DATETIME2 NOT NULL
);

CREATE INDEX hraeu_idx_1 ON history_role_assignment_excepted_users (dato);