CREATE TABLE audit_log (
    id                      BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    timestamp               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address              VARCHAR(64) NOT NULL,
    username                VARCHAR(128) NOT NULL,
    entity_type             VARCHAR(64) NOT NULL,
    entity_id               VARCHAR(64) NOT NULL,
    event_type              VARCHAR(64) NOT NULL,
    secondary_entity_type   VARCHAR(64),
    secondary_entity_id     VARCHAR(64)
);
