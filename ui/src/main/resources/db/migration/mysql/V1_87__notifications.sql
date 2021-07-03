CREATE TABLE notifications (
  id                     BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  notification_type      VARCHAR(255) NOT NULL,
  affected_entity_uuid   VARCHAR(36) NOT NULL,
  affected_entity_type   VARCHAR(255) NOT NULL,
  affected_entity_name   VARCHAR(255) NOT NULL,
  active                 BOOLEAN NOT NULL DEFAULT FALSE,
  message                VARCHAR(1000),
  created                TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_updated           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  admin_uuid             VARCHAR(36),
  admin_name             VARCHAR(255)
);