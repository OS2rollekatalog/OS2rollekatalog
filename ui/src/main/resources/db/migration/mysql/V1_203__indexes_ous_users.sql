ALTER TABLE ous DROP FOREIGN KEY IF EXISTS ous_ibfk_1;
ALTER TABLE ous ADD CONSTRAINT ous_ibfk_1 FOREIGN KEY (manager) REFERENCES users(uuid) ON DELETE SET NULL;

CREATE INDEX idx_user_id_domain ON users (user_id, domain_id);
