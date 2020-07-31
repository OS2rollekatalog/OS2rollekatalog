CREATE TABLE setting (
  id                              BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  setting_key                     VARCHAR(64) NOT NULL,
  setting_value                   TEXT NOT NULL
);

INSERT INTO setting (setting_key, setting_value) VALUES ('kombit_initial_sync_executed','false');
