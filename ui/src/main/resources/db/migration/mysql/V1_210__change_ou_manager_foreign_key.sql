ALTER TABLE ous DROP FOREIGN KEY ous_ibfk_1;
ALTER TABLE ous ADD CONSTRAINT ous_ibfk_1 FOREIGN KEY (manager) references users (uuid) ON DELETE SET NULL