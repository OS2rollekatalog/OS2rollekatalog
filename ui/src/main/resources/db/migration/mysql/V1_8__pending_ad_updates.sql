CREATE TABLE pending_ad_updates (
    id                      BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    timestamp               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id                 VARCHAR(36) NOT NULL,
    status                  VARCHAR(8) NOT NULL
);