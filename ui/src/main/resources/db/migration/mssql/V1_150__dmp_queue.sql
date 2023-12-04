CREATE TABLE dmp_queue (
  tts                         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  user_uuid                   NVARCHAR(36) NOT NULL PRIMARY KEY,

  CONSTRAINT fk_dmp_queue_user FOREIGN KEY (user_uuid) REFERENCES users (uuid) ON DELETE CASCADE
);