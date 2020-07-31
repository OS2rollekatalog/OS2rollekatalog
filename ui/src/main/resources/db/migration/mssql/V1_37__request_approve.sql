CREATE TABLE request_approve (
	id							BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
	requester_uuid              NVARCHAR(36) NOT NULL FOREIGN KEY REFERENCES users(uuid) ON DELETE CASCADE,
	manager_uuid                NVARCHAR(36),
	assigner_uuid               NVARCHAR(36),
	role_type                   NVARCHAR(64) NOT NULL,
	role_id                     BIGINT NOT NULL,
	reason                      NTEXT,
	reject_reason               NTEXT,
	role_assigner_notified      BIT NOT NULL DEFAULT 0,
	status                      NVARCHAR(64) NOT NULL,
	request_timestamp           DATETIME2 NOT NULL DEFAULT GETDATE(),
	status_timestamp            DATETIME2 NULL
);