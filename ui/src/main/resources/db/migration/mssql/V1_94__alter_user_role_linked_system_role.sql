ALTER TABLE user_roles ADD 
  linked_system_role BIGINT NULL FOREIGN KEY REFERENCES system_roles(id) ON DELETE CASCADE,
  linked_system_role_prefix NVARCHAR(64) NULL;
