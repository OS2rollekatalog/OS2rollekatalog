CREATE TABLE pending_ad_updates (
    id                      BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
    timestamp               DATETIME2 NOT NULL DEFAULT GETDATE(),
    user_id                 NVARCHAR(36),
    status                  NVARCHAR(8)
);