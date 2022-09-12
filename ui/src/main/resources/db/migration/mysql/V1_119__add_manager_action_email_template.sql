ALTER TABLE user_roles ADD COLUMN require_manager_action BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE user_roles ADD COLUMN send_to_substitutes BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE user_roles ADD COLUMN send_to_authorization_managers BOOLEAN NOT NULL DEFAULT 0;

ALTER TABLE email_queue MODIFY email TEXT NULL;

CREATE TABLE user_role_email_template (
  id                        BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  title                     VARCHAR(255) NOT NULL,
  message                   MEDIUMTEXT NOT NULL,
  user_role_id              BIGINT NOT NULL,
  
  CONSTRAINT fk_UserRoleEmailTemplate_user_role FOREIGN KEY (user_role_id) REFERENCES user_roles(id) ON DELETE CASCADE
);

