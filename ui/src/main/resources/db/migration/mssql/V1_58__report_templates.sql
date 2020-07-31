CREATE TABLE report_template (
  id                        BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),

  name                      NVARCHAR(128) NOT NULL,
  show_users                NVARCHAR(6) NOT NULL,
  show_ous                  NVARCHAR(6) NOT NULL,
  show_user_roles           NVARCHAR(6) NOT NULL,
  show_kle                  NVARCHAR(6) NOT NULL,
  show_it_systems           NVARCHAR(6) NOT NULL,
  show_inactive_users       NVARCHAR(6) NOT NULL
);
