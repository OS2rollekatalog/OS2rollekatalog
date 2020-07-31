CREATE TABLE ldap_log_entry (
    id                      BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
    timestamp               DATETIME2 NOT NULL DEFAULT GETDATE(),
    operation               NVARCHAR(64),
    user_id                 NVARCHAR(128),
    systemrole              NVARCHAR(128),
    description             NVARCHAR(MAX)
);