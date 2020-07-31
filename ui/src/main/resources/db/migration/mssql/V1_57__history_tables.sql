CREATE TABLE history_role_assignments (
  id                        BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),

  dato                      DATE NOT NULL,

  user_uuid                 NVARCHAR(36) NOT NULL,

  role_id                   BIGINT NOT NULL,
  role_name                 NVARCHAR(64) NOT NULL,
  role_it_system_id         BIGINT NOT NULL,
  role_it_system_name       NVARCHAR(64) NOT NULL,
  role_role_group           NVARCHAR(64),

  assigned_through_type     NVARCHAR(64) NOT NULL,
  assigned_through_uuid     NVARCHAR(36),
  assigned_through_name     NVARCHAR(512),

  assigned_by_user_id       NVARCHAR(255) NOT NULL,
  assigned_by_name          NVARCHAR(255) NOT NULL,
  assigned_when             DATETIME2 NOT NULL
);

CREATE INDEX hra_idx_1 ON history_role_assignments (dato);

CREATE TABLE history_ou_role_assignments (
  id                        BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),

  dato                      DATE NOT NULL,

  ou_uuid                   NVARCHAR(36) NOT NULL,

  role_id                   BIGINT NOT NULL,
  role_name                 NVARCHAR(64) NOT NULL,
  role_it_system_id         BIGINT NOT NULL,
  role_it_system_name       NVARCHAR(64) NOT NULL,
  role_role_group           NVARCHAR(64),

  assigned_through_type     NVARCHAR(64) NOT NULL,
  assigned_through_uuid     NVARCHAR(36),
  assigned_through_name     NVARCHAR(512),

  assigned_by_user_id       NVARCHAR(255) NOT NULL,
  assigned_by_name          NVARCHAR(255) NOT NULL,
  assigned_when             DATETIME2 NOT NULL
);

CREATE INDEX hora_idx_1 ON history_ou_role_assignments (dato);

CREATE TABLE history_kle_assignments (
  id                        BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),

  dato                      DATE NOT NULL,

  user_uuid                 NVARCHAR(36) NOT NULL,

  assignment_type           NVARCHAR(16) NOT NULL,
  kle_values                TEXT
);

CREATE INDEX hka_idx_1 ON history_kle_assignments (dato);

CREATE TABLE history_ou_kle_assignments (
  id                        BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),

  dato                      DATE NOT NULL,

  ou_uuid                   NVARCHAR(36) NOT NULL,

  assignment_type           NVARCHAR(16) NOT NULL,
  kle_values                TEXT
);

CREATE INDEX hoka_idx_1 ON history_ou_kle_assignments (dato);

CREATE TABLE history_ous (
  id                            BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),

  dato                          DATE NOT NULL,

  ou_uuid                       NVARCHAR(36) NOT NULL,
  ou_name                       NVARCHAR(255) NOT NULL,
  ou_parent_uuid                NVARCHAR(36),
  ou_manager_uuid               NVARCHAR(36)
);

CREATE INDEX ho_idx_1 ON history_ous (dato);

CREATE TABLE history_ous_users (
  history_ous_id                BIGINT NOT NULL FOREIGN KEY REFERENCES history_ous(id) ON DELETE CASCADE,

  user_uuid                     NVARCHAR(36) NOT NULL
);

CREATE TABLE history_managers (
  id                            BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),

  dato                          DATE NOT NULL,

  user_uuid                     NVARCHAR(36) NOT NULL,
  user_name                     NVARCHAR(255) NOT NULL
);

CREATE INDEX hm_idx_1 ON history_managers (dato);

CREATE TABLE history_users (
  id                        BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),

  dato                      DATE NOT NULL,

  user_uuid                 NVARCHAR(36) NOT NULL,
  user_ext_uuid             NVARCHAR(36) NOT NULL,
  user_name                 NVARCHAR(255) NOT NULL,
  user_user_id              NVARCHAR(64),
  user_active               BIT NOT NULL
);

CREATE INDEX hu_idx_1 ON history_users (dato);

CREATE TABLE history_it_systems (
  id                            BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),

  dato                          DATE NOT NULL,

  -- it_system
  it_system_id                  BIGINT NOT NULL,
  it_system_name                NVARCHAR(64) NOT NULL,
  it_system_hidden              BIT NOT NULL
);

CREATE INDEX hit_idx_1 ON history_it_systems (dato);

CREATE TABLE history_system_roles (
  id                            BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
  
  history_it_systems_id         BIGINT NOT NULL FOREIGN KEY REFERENCES history_it_systems(id) ON DELETE CASCADE,

  system_role_id                BIGINT NOT NULL,
  system_role_name              NVARCHAR(128) NOT NULL,
  system_role_description       TEXT
);

CREATE TABLE history_user_roles (
  id                            BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),

  history_it_systems_id         BIGINT NOT NULL FOREIGN KEY REFERENCES history_it_systems(id) ON DELETE CASCADE,
  
  user_role_id                  BIGINT NOT NULL,
  user_role_name                NVARCHAR(64),
  user_role_description         TEXT,
  user_role_delegated_from_cvr  NVARCHAR(8)
);

CREATE TABLE history_user_roles_system_roles (
  id                           BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
  
  history_user_roles_id        BIGINT NOT NULL FOREIGN KEY REFERENCES history_user_roles(id) ON DELETE CASCADE,

  system_role_assignments_id   BIGINT NOT NULL,
  system_role_name             NVARCHAR(128)
);

CREATE TABLE history_user_roles_system_role_constraints (
  id                                     BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
  
  history_user_roles_system_roles_id     BIGINT NOT NULL FOREIGN KEY REFERENCES history_user_roles_system_roles(id) ON DELETE CASCADE,

  constraint_name                        NVARCHAR(64),
  constraint_value_type                  NVARCHAR(64),
  constraint_value                       TEXT
);
