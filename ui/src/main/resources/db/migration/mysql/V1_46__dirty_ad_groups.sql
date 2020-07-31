CREATE TABLE dirty_ad_groups (
    id                                  BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    timestamp                           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    identifier                          VARCHAR(128) NOT NULL,
    it_system_id                        BIGINT DEFAULT NULL
);
