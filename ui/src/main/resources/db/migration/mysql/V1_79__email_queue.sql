CREATE TABLE email_queue (
  id                        BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  title                     VARCHAR(255) NOT NULL,
  message                   MEDIUMTEXT NOT NULL,
  email                     VARCHAR(255),
  delivery_tts              TIMESTAMP NOT NULL,
  email_template_id         BIGINT NOT NULL,
  
  FOREIGN KEY (email_template_id) REFERENCES email_templates(id) ON DELETE CASCADE
);

CREATE TABLE email_attachment_file (
  id                        BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  content                   MEDIUMBLOB NOT NULL,
  filename                  VARCHAR(255) NOT NULL,
  email_queue_id 			BIGINT NOT NULL,
  
  FOREIGN KEY (email_queue_id) REFERENCES email_queue(id) ON DELETE CASCADE
);