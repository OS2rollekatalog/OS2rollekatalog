CREATE TABLE pending_kombit_updates (
    id                      BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    user_role_uuid          VARCHAR(36),
    user_role_id            BIGINT NOT NULL,
    event_type              VARCHAR(8) NOT NULL
);

ALTER TABLE user_roles ADD COLUMN uuid VARCHAR(36);
ALTER TABLE user_roles ADD COLUMN delegated_from_cvr VARCHAR(8);
