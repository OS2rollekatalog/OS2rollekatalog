CREATE TABLE history_titles (
  id                            BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,

  -- date for entry
  dato                          DATE NOT NULL,

  -- title
  title_uuid                    VARCHAR(36) NOT NULL,
  title_name                    VARCHAR(255) NOT NULL,

  INDEX(dato)
);

CREATE TABLE history_title_role_assignments (
  id                        BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,

  -- date for entry
  dato                      DATE NOT NULL,

  -- user reference
  title_uuid                VARCHAR(36) NOT NULL,

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
