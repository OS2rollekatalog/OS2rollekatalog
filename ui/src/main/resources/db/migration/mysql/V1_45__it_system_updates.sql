CREATE TABLE it_system_updates (
    id                                  BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    timestamp                           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    event_type                          VARCHAR(64) NOT NULL,
    it_system_id                        BIGINT DEFAULT NULL,
    system_role_id                      BIGINT DEFAULT NULL,
    system_role_name                    VARCHAR(64) NULL,
    system_role_identifier              VARCHAR(128) NULL
);
