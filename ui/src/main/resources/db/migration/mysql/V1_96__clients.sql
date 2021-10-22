CREATE TABLE client (
  id                              BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  name                            VARCHAR(64) NOT NULL,
  api_key                         VARCHAR(36) NOT NULL,
  access_role                     VARCHAR(36) NOT NULL,
  CONSTRAINT UC_ApiKey UNIQUE (api_key)
)