CREATE TABLE report_template_user (
  user_uuid                     NVARCHAR(36) NOT NULL,
  template_id                   BIGINT NOT NULL,

  FOREIGN KEY (template_id) REFERENCES report_template(id) ON DELETE CASCADE,
  FOREIGN KEY (user_uuid) REFERENCES users(uuid) ON DELETE CASCADE
);