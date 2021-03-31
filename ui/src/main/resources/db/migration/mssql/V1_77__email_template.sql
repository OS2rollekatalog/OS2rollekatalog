CREATE TABLE email_templates (
  id                     BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
  title                  NVARCHAR(255) NOT NULL,
  message                NTEXT NOT NULL,
  template_type          NVARCHAR(64) NOT NULL
);
