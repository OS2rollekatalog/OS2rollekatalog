ALTER TABLE client ADD version NVARCHAR(64) NULL;
ALTER TABLE client ADD application_identifier NVARCHAR(255) NULL;
ALTER TABLE client ADD newest_version NVARCHAR(255) NULL;
ALTER TABLE client ADD minimum_version NVARCHAR(255) NULL;
ALTER TABLE client ADD version_status NVARCHAR(255) NOT NULL DEFAULT 'UNKNOWN';