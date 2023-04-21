INSERT INTO it_systems (name, identifier, system_type) VALUES ('NemLog-in', 'NemLogin', 'NEMLOGIN');

CREATE TABLE pending_nemlogin_updates (
    id                      BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    user_role_uuid          VARCHAR(36),
    user_role_id            BIGINT NOT NULL,
    event_type              VARCHAR(6) NOT NULL,
    created                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    failed                  BOOLEAN NOT NULL DEFAULT FALSE
);

ALTER TABLE users ADD COLUMN nemlogin_uuid VARCHAR(36) NULL;

ALTER TABLE user_roles ADD COLUMN nemlogin_constraint_type VARCHAR(36) NOT NULL DEFAULT 'NONE';
ALTER TABLE user_roles ADD COLUMN nemlogin_constraint_value VARCHAR(255) NULL;