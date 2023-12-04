CREATE TABLE dmp_queue (
  tts                         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  user_uuid                   VARCHAR(36) NOT NULL PRIMARY KEY,
  
  FOREIGN KEY fk_dmp_queue_user (user_uuid) REFERENCES users (uuid) ON DELETE CASCADE
);
