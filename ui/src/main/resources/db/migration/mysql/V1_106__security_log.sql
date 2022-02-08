CREATE TABLE security_log (
  id                        BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  timestamp                 TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  client_id                 BIGINT NOT NULL,
  clientname                VARCHAR(128) NOT NULL,
  method                    VARCHAR(32) NOT NULL,
  request                   TEXT NOT NULL,
  ip_address                VARCHAR(32) NOT NULL
);