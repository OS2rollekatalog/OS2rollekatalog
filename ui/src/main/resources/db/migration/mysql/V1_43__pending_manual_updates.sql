CREATE TABLE pending_manual_updates (
    id                      BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    timestamp               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id                 VARCHAR(64) NOT NULL,
    it_system_id            BIGINT DEFAULT NULL
);
