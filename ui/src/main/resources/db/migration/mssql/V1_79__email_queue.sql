CREATE TABLE email_queue (
  id                        BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
  title                     NVARCHAR(255) NOT NULL,
  message                   NTEXT NOT NULL,
  email                     NVARCHAR(255),
  delivery_tts              DATETIME2 NOT NULL,
  email_template_id         BIGINT NOT NULL,
  
  FOREIGN KEY (email_template_id) REFERENCES email_templates(id) ON DELETE CASCADE
);

CREATE TABLE email_attachment_file (
  id                        BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
  content                   VARBINARY(max) NOT NULL,
  filename                  NVARCHAR(255) NOT NULL,
  email_queue_id 			BIGINT NOT NULL,
  
  FOREIGN KEY (email_queue_id) REFERENCES email_queue(id) ON DELETE CASCADE
);