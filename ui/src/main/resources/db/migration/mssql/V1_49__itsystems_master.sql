CREATE TABLE it_systems_master (
    id                          BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
    master_id                   NVARCHAR(36) NOT NULL,
	name						NVARCHAR(64) NOT NULL,
    last_modified               DATETIME2 NOT NULL DEFAULT GETDATE()
);

ALTER TABLE it_systems ADD subscribed_to NVARCHAR(36);