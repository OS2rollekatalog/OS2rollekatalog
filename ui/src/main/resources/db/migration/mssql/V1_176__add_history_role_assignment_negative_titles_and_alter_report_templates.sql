CREATE TABLE history_role_assignment_negative_titles (
    id                          BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    dato                        DATE NOT NULL,
    ou_uuid                     NVARCHAR(36) NOT NULL,
    title_uuids                 NVARCHAR(MAX) NOT NULL,
    role_id                     BIGINT NOT NULL,
    role_name                   NVARCHAR(128) NOT NULL,
    role_it_system_id           BIGINT NOT NULL,
    role_it_system_name         NVARCHAR(64) NOT NULL,
    role_role_group             NVARCHAR(128),
    role_role_group_id          BIGINT NULL,
    assigned_by_user_id         NVARCHAR(255) NOT NULL,
    assigned_by_name            NVARCHAR(255) NOT NULL,
    assigned_when               DATETIME NOT NULL,
    start_date                  DATE NULL,
    stop_date                   DATE NULL
);
GO

CREATE INDEX idx_dato ON history_role_assignment_negative_titles(dato);
GO

ALTER TABLE report_template ADD show_negative_roles NVARCHAR(6) NOT NULL;
GO