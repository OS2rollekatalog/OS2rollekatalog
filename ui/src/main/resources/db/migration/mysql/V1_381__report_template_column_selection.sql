CREATE TABLE report_template_column (
  id                        BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  template_id               BIGINT NOT NULL,
  content_type              VARCHAR(64) NOT NULL,
  column_key                VARCHAR(64) NOT NULL,

  FOREIGN KEY (template_id) REFERENCES report_template(id) ON DELETE CASCADE
);

CREATE INDEX idx_report_template_column_template_id ON report_template_column(template_id);
