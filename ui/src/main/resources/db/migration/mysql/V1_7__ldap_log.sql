CREATE TABLE ldap_log_entry (
    id                      BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    timestamp               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    operation               VARCHAR(64) DEFAULT NULL,
    user_id                 VARCHAR(128) DEFAULT NULL,
    systemrole              VARCHAR(128) DEFAULT NULL,
    description             TEXT
);