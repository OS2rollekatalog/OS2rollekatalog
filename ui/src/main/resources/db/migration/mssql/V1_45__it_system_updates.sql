CREATE TABLE it_system_updates (
    id                                  BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
    timestamp                           DATETIME2 NOT NULL DEFAULT GETDATE(),
    event_type                          NVARCHAR(64) NOT NULL,
    it_system_id                        BIGINT DEFAULT NULL,
    system_role_id                      BIGINT DEFAULT NULL,
    system_role_name                    NVARCHAR(64) NULL,
    system_role_identifier              NVARCHAR(128) NULL
);
