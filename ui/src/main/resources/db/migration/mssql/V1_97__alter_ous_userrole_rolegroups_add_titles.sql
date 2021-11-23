ALTER TABLE ou_roles
  ADD contains_titles BIT NOT NULL DEFAULT 0;

ALTER TABLE ou_rolegroups
  ADD contains_titles BIT NOT NULL DEFAULT 0;

GO

CREATE TABLE ou_roles_titles (
  id                       BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
  ou_roles_id              BIGINT NOT NULL CONSTRAINT fk_ou_roles_titles_ou_roles REFERENCES ou_roles(id) ON DELETE CASCADE,
  title_uuid               NVARCHAR(36) NOT NULL CONSTRAINT fk_ou_roles_titles_titles REFERENCES titles (uuid) ON DELETE CASCADE
);

CREATE TABLE ou_rolegroups_titles (
  id                       BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
  ou_rolegroups_id         BIGINT NOT NULL CONSTRAINT fk_ou_rolegroups_titles_ou_rolegroups REFERENCES ou_rolegroups (id) ON DELETE CASCADE,
  title_uuid               NVARCHAR(36) NOT NULL CONSTRAINT fk_ou_rolegroups_titles_titles REFERENCES titles (uuid) ON DELETE CASCADE
);

-- migrate existing assignments on titles into new tables
INSERT INTO ou_roles (ou_uuid, role_id, start_date, stop_date, contains_titles, assigned_by_user_id, assigned_by_name)
SELECT
  tro.ou_uuid, tr.role_id, MIN(tr.start_date), MAX(tr.stop_date), 1, 'system', 'system'
FROM title_roles tr
INNER JOIN title_roles_ous tro ON tro.title_roles_id = tr.id
GROUP BY tro.ou_uuid, tr.role_id;

INSERT INTO ou_roles_titles (ou_roles_id, title_uuid)
SELECT
  our.id, tr.title_uuid
FROM title_roles tr
INNER JOIN title_roles_ous tro ON tro.title_roles_id = tr.id
INNER JOIN ou_roles our ON our.role_id = tr.role_id AND our.ou_uuid = tro.ou_uuid AND our.contains_titles = 1;

INSERT INTO ou_rolegroups (ou_uuid, rolegroup_id, start_date, stop_date, contains_titles, assigned_by_user_id, assigned_by_name)
SELECT
  tro.ou_uuid, tr.rolegroup_id, MIN(tr.start_date), MAX(tr.stop_date), 1, 'system', 'system'
FROM title_rolegroups tr
INNER JOIN title_rolegroups_ous tro ON tro.title_rolegroups_id = tr.id
GROUP BY tro.ou_uuid, tr.rolegroup_id;

INSERT INTO ou_rolegroups_titles (ou_rolegroups_id, title_uuid)
SELECT
  our.id, tr.title_uuid
FROM title_rolegroups tr
INNER JOIN title_rolegroups_ous tro ON tro.title_rolegroups_id = tr.id
INNER JOIN ou_rolegroups our ON our.rolegroup_id = tr.rolegroup_id AND our.ou_uuid = tro.ou_uuid AND our.contains_titles = 1;

DROP TABLE title_roles_ous;
DROP TABLE title_roles;
DROP TABLE title_rolegroups_ous;
DROP TABLE title_rolegroups;

-- remove old and create new history tables
DROP TABLE history_title_role_assignments;

-- History tables for exceptions
CREATE TABLE history_role_assignment_titles (
  id                        BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
  dato                      DATE NOT NULL,
  ou_uuid                   NVARCHAR(36) NOT NULL,
  title_uuids               TEXT NOT NULL,
  role_id                   BIGINT NOT NULL,
  role_name                 NVARCHAR(64) NOT NULL,
  role_it_system_id         BIGINT NOT NULL,
  role_it_system_name       NVARCHAR(64) NOT NULL,
  role_role_group           NVARCHAR(64),
  assigned_by_user_id       NVARCHAR(255) NOT NULL,
  assigned_by_name          NVARCHAR(255) NOT NULL,
  assigned_when             DATETIME NOT NULL,

  INDEX IX_history_title_role_assignments_dato (dato)
);
