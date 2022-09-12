ALTER TABLE user_roles ADD require_manager_action BIT NOT NULL DEFAULT 0;
ALTER TABLE user_roles ADD send_to_substitutes BIT NOT NULL DEFAULT 0;
ALTER TABLE user_roles ADD send_to_authorization_managers BIT NOT NULL DEFAULT 0;

ALTER TABLE email_queue ALTER COLUMN email TEXT NULL;

CREATE TABLE user_role_email_template (
  id                        BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
  title                     NVARCHAR(255) NOT NULL,
  message                   TEXT NOT NULL,
  user_role_id              BIGINT NOT NULL CONSTRAINT fk_UserRoleEmailTemplate_user_role REFERENCES user_roles(id) ON DELETE CASCADE
);

