CREATE TABLE report_template (
  id                        BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,

  name                      VARCHAR(128) NOT NULL,
  show_users                VARCHAR(6) NOT NULL,
  show_ous                  VARCHAR(6) NOT NULL,
  show_user_roles           VARCHAR(6) NOT NULL,
  show_kle                  VARCHAR(6) NOT NULL,
  show_it_systems           VARCHAR(6) NOT NULL,
  show_inactive_users       VARCHAR(6) NOT NULL
);
