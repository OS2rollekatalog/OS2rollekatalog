ALTER TABLE users ADD cpr NVARCHAR(10) NULL;

INSERT INTO it_systems (name, identifier, system_type) VALUES ('KSP/CICS', 'KSPCICS', 'KSPCICS');

CREATE TABLE ksp_cics_unmatched_users (
  id                      BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
  user_id                 NVARCHAR(64) NOT NULL,
  cpr                     NVARCHAR(10)
);

CREATE TABLE users_alt_accounts (
  id                      BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
  user_uuid               NVARCHAR(36) NOT NULL FOREIGN KEY REFERENCES users(uuid) ON DELETE CASCADE,
  account_type            NVARCHAR(64) NOT NULL,
  account_user_id         NVARCHAR(64) NOT NULL
);

CREATE TABLE dirty_ksp_cics_user_profiles (
  id                      BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
  timestamp               DATETIME2 NOT NULL DEFAULT GETDATE(),
  identifier              NVARCHAR(128) NOT NULL
);
