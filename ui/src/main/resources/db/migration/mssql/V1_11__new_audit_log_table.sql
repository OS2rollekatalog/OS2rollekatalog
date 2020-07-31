CREATE TABLE audit_log (
    id                      BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
    timestamp               DATETIME2 NOT NULL DEFAULT GETDATE(),
    ip_address              NVARCHAR(64) NOT NULL,
    username                NVARCHAR(128) NOT NULL,
    entity_type             NVARCHAR(64) NOT NULL,
    entity_id               NVARCHAR(64) NOT NULL,
    event_type              NVARCHAR(64) NOT NULL,
    secondary_entity_type   NVARCHAR(64),
    secondary_entity_id     NVARCHAR(64)
);
