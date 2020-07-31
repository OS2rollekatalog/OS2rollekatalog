CREATE TABLE setting (
  id                              BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
  setting_key                     NVARCHAR(64) NOT NULL,
  setting_value                   NTEXT NOT NULL
);

INSERT INTO setting (setting_key, setting_value) VALUES ('kombit_initial_sync_executed','false');
