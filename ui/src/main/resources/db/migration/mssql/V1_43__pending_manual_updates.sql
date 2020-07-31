CREATE TABLE pending_manual_updates (
	id						BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
    timestamp               DATETIME2 NOT NULL DEFAULT GETDATE(),
    user_id                 NVARCHAR(64) NOT NULL,
    it_system_id            BIGINT DEFAULT NULL
);
