ALTER TABLE it_system_updates ADD system_role_description NTEXT;
ALTER TABLE it_system_updates ADD it_system_name NVARCHAR(64);
ALTER TABLE it_system_updates ADD system_role_constraint_changed BIT NOT NULL DEFAULT 0;
