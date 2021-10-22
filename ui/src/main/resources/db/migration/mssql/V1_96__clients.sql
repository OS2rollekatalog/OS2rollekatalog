CREATE TABLE client (
  id                              BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
  name                            NVARCHAR(64) NOT NULL,
  api_key                         NVARCHAR(36) NOT NULL,
  access_role                     NVARCHAR(36) NOT NULL,
  CONSTRAINT UC_ApiKey UNIQUE (api_key)
)