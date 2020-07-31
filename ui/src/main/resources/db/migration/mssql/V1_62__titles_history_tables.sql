CREATE TABLE history_titles (
  id                        BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),

  -- date for entry
  dato                      DATE NOT NULL,

  -- title
  title_uuid                NVARCHAR(36) NOT NULL,
  title_name                NVARCHAR(255) NOT NULL
);

CREATE INDEX ht_idx_1 ON history_titles (dato);

CREATE TABLE history_title_role_assignments (
  id                        BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),

  dato                      DATE NOT NULL,

  title_uuid                NVARCHAR(36) NOT NULL,

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

CREATE INDEX htra_idx_1 ON history_title_role_assignments (dato);