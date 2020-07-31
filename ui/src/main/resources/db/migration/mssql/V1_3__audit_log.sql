CREATE TABLE audit_log_entry (
    id                      BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
    timestamp               DATETIME2 NOT NULL DEFAULT GETDATE(),
    username                NVARCHAR(128),
    entity                  NVARCHAR(64),
    entity_id               NVARCHAR(64),
    message                 NVARCHAR(MAX)
);