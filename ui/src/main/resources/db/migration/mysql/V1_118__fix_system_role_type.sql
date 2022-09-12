UPDATE system_roles SET role_type = 'BOTH' WHERE role_type IS NULL;
ALTER TABLE system_roles MODIFY COLUMN role_type VARCHAR(64) NOT NULL DEFAULT 'BOTH';
