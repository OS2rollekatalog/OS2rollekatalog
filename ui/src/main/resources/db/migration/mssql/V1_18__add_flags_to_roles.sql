ALTER TABLE user_roles ADD user_only BIT NOT NULL DEFAULT 0;
ALTER TABLE user_roles ADD ou_inherit_allowed BIT NOT NULL DEFAULT 0;
ALTER TABLE rolegroup ADD user_only BIT NOT NULL DEFAULT 0;
ALTER TABLE rolegroup ADD ou_inherit_allowed BIT NOT NULL DEFAULT 0;
