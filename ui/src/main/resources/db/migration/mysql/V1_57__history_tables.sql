CREATE TABLE history_role_assignments (
  id                        BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,

  -- date for entry
  dato                      DATE NOT NULL,

  -- user reference
  user_uuid                 VARCHAR(36) NOT NULL,

  -- role
  role_id                   BIGINT NOT NULL,
  role_name                 VARCHAR(64) NOT NULL,
  role_it_system_id         BIGINT NOT NULL,
  role_it_system_name       VARCHAR(64) NOT NULL,
  role_role_group           VARCHAR(64),

  -- assigned through
  assigned_through_type     VARCHAR(64) NOT NULL,
  assigned_through_uuid     VARCHAR(36),
  assigned_through_name     VARCHAR(512),

  -- assigned by, and when
  assigned_by_user_id       VARCHAR(255) NOT NULL,
  assigned_by_name          VARCHAR(255) NOT NULL,
  assigned_when             DATETIME NOT NULL,
  
  INDEX(dato)
);

CREATE TABLE history_ou_role_assignments (
  id                        BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,

  -- date for entry
  dato                      DATE NOT NULL,

  -- user reference
  ou_uuid                   VARCHAR(36) NOT NULL,

  -- role
  role_id                   BIGINT NOT NULL,
  role_name                 VARCHAR(64) NOT NULL,
  role_it_system_id         BIGINT NOT NULL,
  role_it_system_name       VARCHAR(64) NOT NULL,
  role_role_group           VARCHAR(64),

  -- assigned through
  assigned_through_type     VARCHAR(64) NOT NULL,
  assigned_through_uuid     VARCHAR(36),
  assigned_through_name     VARCHAR(512),

  -- assigned by, and when
  assigned_by_user_id       VARCHAR(255) NOT NULL,
  assigned_by_name          VARCHAR(255) NOT NULL,
  assigned_when             DATETIME NOT NULL,
  
  INDEX(dato)
);

CREATE TABLE history_kle_assignments (
  id                        BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,

  -- date for entry
  dato                      DATE NOT NULL,

  -- user
  user_uuid                 VARCHAR(36) NOT NULL,

  -- kle
  assignment_type           VARCHAR(16) NOT NULL,
  kle_values                TEXT,

  INDEX(dato)
);

CREATE TABLE history_ou_kle_assignments (
  id                        BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,

  -- date for entry
  dato                      DATE NOT NULL,

  -- use
  ou_uuid                   VARCHAR(36) NOT NULL,

  -- kle
  assignment_type           VARCHAR(16) NOT NULL,
  kle_values                TEXT,

  INDEX(dato)
);

CREATE TABLE history_ous (
  id                            BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,

  -- date for entry
  dato                          DATE NOT NULL,

  -- ou
  ou_uuid                       VARCHAR(36) NOT NULL,
  ou_name                       VARCHAR(255) NOT NULL,
  ou_parent_uuid                VARCHAR(36),
  ou_manager_uuid               VARCHAR(36),

  INDEX(dato)
);

CREATE TABLE history_ous_users (

  -- back reference
  history_ous_id                BIGINT NOT NULL,

  -- user
  user_uuid                     VARCHAR(36) NOT NULL,

  FOREIGN KEY (history_ous_id) REFERENCES history_ous(id) ON DELETE CASCADE
);

CREATE TABLE history_managers (
  id                            BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,

  -- date for entry
  dato                          DATE NOT NULL,

  user_uuid                     VARCHAR(36) NOT NULL,
  user_name                     VARCHAR(255) NOT NULL,

  INDEX(dato)
);

CREATE TABLE history_users (
  id                        BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,

  -- date for entry
  dato                      DATE NOT NULL,

  -- user
  user_uuid                 VARCHAR(36) NOT NULL,
  user_ext_uuid             VARCHAR(36) NOT NULL,
  user_name                 VARCHAR(255) NOT NULL,
  user_user_id              VARCHAR(64),
  user_active               BOOLEAN NOT NULL,

  INDEX(dato)
);

CREATE TABLE history_it_systems (
  id                            BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,

  -- date for entry
  dato                          DATE NOT NULL,

  -- it_system
  it_system_id                  BIGINT NOT NULL,
  it_system_name                VARCHAR(64) NOT NULL,
  it_system_hidden              BOOLEAN NOT NULL,

  INDEX(dato)
);

CREATE TABLE history_system_roles (
  id                            BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  
  -- back reference
  history_it_systems_id         BIGINT NOT NULL,

  -- system role
  system_role_id                BIGINT NOT NULL,
  system_role_name              VARCHAR(128) NOT NULL,
  system_role_description       TEXT,
  
  FOREIGN KEY (history_it_systems_id) REFERENCES history_it_systems(id) ON DELETE CASCADE
);

CREATE TABLE history_user_roles (
  id                            BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,

  -- back reference
  history_it_systems_id         BIGINT NOT NULL,
  
  -- user_role
  user_role_id                  BIGINT NOT NULL,
  user_role_name                VARCHAR(64),
  user_role_description         TEXT,
  user_role_delegated_from_cvr  VARCHAR(8),
  
  FOREIGN KEY (history_it_systems_id) REFERENCES history_it_systems(id) ON DELETE CASCADE
);

CREATE TABLE history_user_roles_system_roles (
  id                           BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  
  -- back reference
  history_user_roles_id        BIGINT NOT NULL,

  -- system role
  system_role_assignments_id   BIGINT NOT NULL,
  system_role_name             VARCHAR(128),
  
  FOREIGN KEY (history_user_roles_id) REFERENCES history_user_roles(id) ON DELETE CASCADE
);

CREATE TABLE history_user_roles_system_role_constraints (
  id                                     BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  
  -- back reference
  history_user_roles_system_roles_id     BIGINT NOT NULL,

  -- constraint
  constraint_name                        VARCHAR(64),
  constraint_value_type                  VARCHAR(64),
  constraint_value                       TEXT,

  FOREIGN KEY (history_user_roles_system_roles_id) REFERENCES history_user_roles_system_roles(id) ON DELETE CASCADE
);
