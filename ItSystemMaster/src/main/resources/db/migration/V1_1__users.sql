CREATE TABLE user (
  id                              BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  username                        VARCHAR(64) NOT NULL,
  password                        VARCHAR(255) NOT NULL
);
