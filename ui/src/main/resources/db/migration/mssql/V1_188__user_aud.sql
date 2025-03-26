CREATE TABLE revinfo (
  rev BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
  revtstmp BIGINT NULL
);

CREATE TABLE users_aud (
  uuid NVARCHAR(36) NOT NULL,
  rev BIGINT NOT NULL,
  revtype TINYINT NULL,
  user_id NVARCHAR(64) NULL,
  name NVARCHAR(255) NULL,
  deleted BIT NULL,
  disabled BIT NULL,
  email NVARCHAR(255) NULL,
  phone NVARCHAR(255) NULL,
  last_updated DATETIME2 NULL,
  ext_uuid NVARCHAR(36) NULL,
  cpr NVARCHAR(10) NULL,
  nemlogin_uuid NVARCHAR(36) NULL,

  PRIMARY KEY (uuid, rev),
  INDEX rev_idx (rev),
  CONSTRAINT users_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (rev)
);
