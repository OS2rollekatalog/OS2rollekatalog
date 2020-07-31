CREATE TABLE dirty_ad_groups (
    id                                  BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
    timestamp                           DATETIME2 NOT NULL DEFAULT GETDATE(),
    identifier                          NVARCHAR(128) NOT NULL,
    it_system_id                        BIGINT DEFAULT NULL
);
