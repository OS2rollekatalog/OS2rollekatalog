CREATE TABLE security_log (
  id                        BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
  timestamp                 DATETIME2 NOT NULL DEFAULT GETDATE(),
  client_id                 BIGINT NOT NULL,
  clientname                NVARCHAR(128) NOT NULL,
  method                    NVARCHAR(32) NOT NULL,
  request                   NVARCHAR(MAX) NOT NULL,
  ip_address                NVARCHAR(32) NOT NULL
);