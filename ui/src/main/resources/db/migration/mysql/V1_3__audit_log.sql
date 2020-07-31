CREATE TABLE audit_log_entry (
    id                      BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    timestamp               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user                    VARCHAR(128),
    entity                  VARCHAR(64),
    entity_id               VARCHAR(64),
    message                 TEXT
);