ALTER TABLE system_roles ADD COLUMN role_type VARCHAR(64);
UPDATE system_roles set role_type = 'BOTH';