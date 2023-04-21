CREATE TABLE domains (
  id                           BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
  name                         NVARCHAR(255) NOT NULL,

  CONSTRAINT c_domains_name UNIQUE (name)
);

INSERT INTO domains(name) VALUES('Administrativt');

ALTER TABLE users ADD domain_id BIGINT NULL;

GO

UPDATE users SET domain_id = (SELECT id FROM domains WHERE name = 'Administrativt');
ALTER TABLE users ALTER COLUMN domain_id BIGINT NOT NULL;
ALTER TABLE users ADD CONSTRAINT fk_user_domain FOREIGN KEY (domain_id) REFERENCES domains(id) ON DELETE CASCADE;