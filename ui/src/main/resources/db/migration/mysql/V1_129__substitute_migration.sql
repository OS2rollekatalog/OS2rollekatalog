-- create new mapping table for users and substitutes
CREATE TABLE users_manager_substitute (
    id                                      BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    manager_uuid                            VARCHAR(36) NOT NULL,
    substitute_uuid                         VARCHAR(36) NOT NULL,
    ou_uuid                                 VARCHAR(36) NOT NULL,
    assigned_by                             VARCHAR(255) NULL,
    assigned_tts                            TIMESTAMP NULL,

    FOREIGN KEY (manager_uuid) REFERENCES users (uuid) ON DELETE CASCADE,
    FOREIGN KEY (substitute_uuid) REFERENCES users (uuid) ON DELETE CASCADE,
    FOREIGN KEY (ou_uuid) REFERENCES ous (uuid) ON DELETE CASCADE
);

-- migrate data
INSERT INTO users_manager_substitute (manager_uuid, substitute_uuid, ou_uuid, assigned_by, assigned_tts) 
SELECT
      u.uuid as manager_uuid,
      u.manager_substitute as substitute_uuid,
      o.uuid,
      u.substitute_assigned_by as assigned_by,
      u.substitute_assigned_tts as assigned_tts
    FROM users u 
    JOIN ous o on o.manager = u.uuid
      WHERE u.manager_substitute IS NOT NULL;

-- delete old manager substiute assignment
ALTER TABLE users DROP FOREIGN KEY users_ibfk_1;
ALTER TABLE users DROP COLUMN manager_substitute, DROP INDEX manager_substitute;
ALTER TABLE users DROP COLUMN substitute_assigned_tts;
ALTER TABLE users DROP COLUMN substitute_assigned_by;
