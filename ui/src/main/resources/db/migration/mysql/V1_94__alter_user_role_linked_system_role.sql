ALTER TABLE user_roles
  ADD COLUMN linked_system_role BIGINT NULL,
  ADD COLUMN linked_system_role_prefix VARCHAR(64) NULL;

ALTER TABLE user_roles ADD CONSTRAINT FOREIGN KEY (linked_system_role) REFERENCES system_roles(id) ON DELETE CASCADE;
