CREATE TABLE revinfo (
  rev                          BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  revtstmp                     BIGINT DEFAULT NULL
);

CREATE TABLE users_aud (
  uuid                         VARCHAR(36) NOT NULL,
  rev                          BIGINT NOT NULL,
  revtype                      TINYINT(4) DEFAULT NULL,
  user_id                      VARCHAR(64) NULL,
  name                         VARCHAR(255) NULL,
  deleted                      TINYINT(1) NULL,
  disabled                     TINYINT(1) NULL,
  email                        VARCHAR(255) NULL,
  phone                        VARCHAR(255) NULL,
  last_updated                 TIMESTAMP NULL,
  ext_uuid                     VARCHAR(36) NULL,
  cpr                          VARCHAR(10) NULL,
  nemlogin_uuid                VARCHAR(36) NULL,

  PRIMARY KEY (uuid,rev),
  KEY rev (rev),
  CONSTRAINT users_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev)
);