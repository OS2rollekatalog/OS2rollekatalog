ALTER TABLE users ADD COLUMN cpr VARCHAR(10) NULL;

INSERT INTO it_systems (name, identifier, system_type) VALUES ('KSP/CICS', 'KSPCICS', 'KSPCICS');

CREATE TABLE ksp_cics_unmatched_users (
  id                      BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  user_id                 VARCHAR(64) NOT NULL,
  cpr                     VARCHAR(10)
);

CREATE TABLE users_alt_accounts (
  id                      BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  user_uuid               VARCHAR(36) NOT NULL,
  account_type            VARCHAR(64) NOT NULL,
  account_user_id         VARCHAR(64) NOT NULL,
  
  FOREIGN KEY (user_uuid) REFERENCES users(uuid) ON DELETE CASCADE
);

CREATE TABLE dirty_ksp_cics_user_profiles (
  id                      BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  timestamp               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  identifier              VARCHAR(128) NOT NULL
);
