CREATE TABLE titles (
  uuid                      VARCHAR(36) PRIMARY KEY,
  name                      VARCHAR(128) NOT NULL,
  active                    TINYINT(1),
  last_updated              TIMESTAMP NULL
);

ALTER TABLE positions ADD COLUMN title_uuid VARCHAR(36) NULL;
ALTER TABLE positions ADD FOREIGN KEY (title_uuid) REFERENCES titles(uuid) ON DELETE CASCADE;

CREATE TABLE title_roles (
  id                        BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  role_id                   BIGINT NOT NULL,
  title_uuid                VARCHAR(36) NOT NULL,
  assigned_by_user_id       VARCHAR(255) NOT NULL,
  assigned_by_name          VARCHAR(255) NOT NULL,
  assigned_timestamp        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  
  FOREIGN KEY (role_id) REFERENCES user_roles(id) ON DELETE CASCADE,
  FOREIGN KEY (title_uuid) REFERENCES titles(uuid) ON DELETE CASCADE
);

CREATE TABLE title_rolegroups (
  id                        BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  rolegroup_id              BIGINT NOT NULL,
  title_uuid                VARCHAR(36) NOT NULL,
  assigned_by_user_id       VARCHAR(255) NOT NULL,
  assigned_by_name          VARCHAR(255) NOT NULL,
  assigned_timestamp        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  
  FOREIGN KEY (rolegroup_id) REFERENCES rolegroup(id) ON DELETE CASCADE,
  FOREIGN KEY (title_uuid) REFERENCES titles(uuid) ON DELETE CASCADE
);
  
CREATE TABLE title_roles_ous (
  title_roles_id                BIGINT NOT NULL,
  ou_uuid                       VARCHAR(36) NOT NULL,
  
  FOREIGN KEY (title_roles_id) REFERENCES title_roles(id) ON DELETE CASCADE
);

CREATE TABLE title_rolegroups_ous (
  title_rolegroups_id           BIGINT NOT NULL,
  ou_uuid                       VARCHAR(36) NOT NULL,
  
  FOREIGN KEY (title_rolegroups_id) REFERENCES title_rolegroups(id) ON DELETE CASCADE
);
