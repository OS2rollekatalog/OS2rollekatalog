CREATE TABLE functions (
    uuid VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    last_updated DATETIME(6) NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (uuid)
);

CREATE TABLE position_function_assignments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    position_id BIGINT NOT NULL,
    function_uuid VARCHAR(36) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_pfa_position FOREIGN KEY (position_id) REFERENCES positions(id) ON DELETE CASCADE,
    CONSTRAINT fk_pfa_function  FOREIGN KEY (function_uuid) REFERENCES functions(uuid) ON DELETE CASCADE
);

CREATE TABLE ou_roles_functions (
  id                       BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  ou_roles_id              BIGINT NOT NULL,
  function_uuid            VARCHAR(36) NOT NULL,

  FOREIGN KEY (function_uuid) REFERENCES functions (uuid) ON DELETE CASCADE,
  FOREIGN KEY (ou_roles_id) REFERENCES ou_roles (id) ON DELETE CASCADE
);

CREATE TABLE ou_rolegroups_functions (
  id                       BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  ou_rolegroups_id         BIGINT NOT NULL,
  function_uuid            VARCHAR(36) NOT NULL,

  FOREIGN KEY (function_uuid) REFERENCES functions (uuid) ON DELETE CASCADE,
  FOREIGN KEY (ou_rolegroups_id) REFERENCES ou_rolegroups (id) ON DELETE CASCADE
);

ALTER TABLE ou_rolegroups add COLUMN contains_functions BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ou_roles add COLUMN contains_functions BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE history_ou_role_assignment_exclusions ADD COLUMN function_uuids TEXT DEFAULT '';

ALTER TABLE history_ou_role_assignment_exclusions MODIFY COLUMN exclusion_type
ENUM('excepted_users', 'titles', 'negative_titles', 'functions') NOT NULL;

ALTER TABLE attestation_ou_role_assignments ADD COLUMN function_uuids TEXT NULL;

ALTER TABLE history_ous_users ADD COLUMN function_uuids TEXT NULL;

CREATE TABLE history_functions (
  id                            BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  dato                          DATE NOT NULL,
  function_uuid                    VARCHAR(36) NOT NULL,
  function_name                    VARCHAR(255) NOT NULL,

  INDEX(dato)
);