CREATE TABLE titles (
  uuid                      NVARCHAR(36) PRIMARY KEY,
  name                      NVARCHAR(128) NOT NULL,
  active                    SMALLINT,
  last_updated              DATETIME2 NULL
);

ALTER TABLE positions ADD title_uuid NVARCHAR(36) NULL REFERENCES titles(uuid);

CREATE TABLE title_roles (
  id                        BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
  role_id                   BIGINT NOT NULL FOREIGN KEY REFERENCES user_roles(id) ON DELETE CASCADE,
  title_uuid                NVARCHAR(36) NOT NULL FOREIGN KEY REFERENCES titles(uuid) ON DELETE CASCADE,
  assigned_by_user_id       NVARCHAR(255) NOT NULL,
  assigned_by_name          NVARCHAR(255) NOT NULL,
  assigned_timestamp        DATETIME2 NOT NULL DEFAULT GETDATE()
);

CREATE TABLE title_rolegroups (
  id                        BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
  rolegroup_id              BIGINT NOT NULL FOREIGN KEY REFERENCES rolegroup(id) ON DELETE CASCADE,
  title_uuid                NVARCHAR(36) NOT NULL FOREIGN KEY REFERENCES titles(uuid) ON DELETE CASCADE,
  assigned_by_user_id       NVARCHAR(255) NOT NULL,
  assigned_by_name          NVARCHAR(255) NOT NULL,
  assigned_timestamp        DATETIME2 NOT NULL DEFAULT GETDATE()
);

CREATE TABLE title_roles_ous (
  title_roles_id                BIGINT NOT NULL FOREIGN KEY REFERENCES title_roles(id) ON DELETE CASCADE,
  ou_uuid                       NVARCHAR(36) NOT NULL
);

CREATE TABLE title_rolegroups_ous (
  title_rolegroups_id           BIGINT NOT NULL FOREIGN KEY REFERENCES title_rolegroups(id) ON DELETE CASCADE,
  ou_uuid                       NVARCHAR(36) NOT NULL
);
