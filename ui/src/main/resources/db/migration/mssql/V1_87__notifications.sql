CREATE TABLE notifications (
  id                     BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
  notification_type      NVARCHAR(255) NOT NULL,
  affected_entity_uuid   NVARCHAR(36) NOT NULL,
  affected_entity_type   NVARCHAR(255) NOT NULL,
  affected_entity_name   NVARCHAR(255) NOT NULL,
  active                 BIT NOT NULL DEFAULT 0,
  message                NVARCHAR(1000),
  created                DATETIME2 NOT NULL DEFAULT GETDATE(),
  last_updated           DATETIME2 NOT NULL DEFAULT GETDATE(),
  admin_uuid             NVARCHAR(36),
  admin_name             NVARCHAR(255)
);