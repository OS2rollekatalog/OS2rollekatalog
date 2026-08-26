CREATE TABLE dirty_nemlogin_users (
  id                    BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  tts                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  user_uuid             VARCHAR(36) NOT NULL,

  CONSTRAINT fk_dirty_nemlogin_users_user FOREIGN KEY (user_uuid) REFERENCES users(uuid) ON DELETE CASCADE
);
