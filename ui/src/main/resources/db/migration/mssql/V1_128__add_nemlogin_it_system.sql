INSERT INTO it_systems (name, identifier, system_type) VALUES ('NemLog-in', 'NemLogin', 'NEMLOGIN');

CREATE TABLE pending_nemlogin_updates (
    id                      BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
    user_role_uuid          VARCHAR(36),
    user_role_id            BIGINT NOT NULL,
    event_type              VARCHAR(6) NOT NULL,
    created                 DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    failed                  BIT NOT NULL DEFAULT 0
);

ALTER TABLE users ADD nemlogin_uuid VARCHAR(36) NULL;

ALTER TABLE user_roles ADD nemlogin_constraint_type VARCHAR(36) NOT NULL DEFAULT 'NONE';
ALTER TABLE user_roles ADD nemlogin_constraint_value VARCHAR(255) NULL;