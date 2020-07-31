ALTER TABLE it_system_updates ADD COLUMN system_role_description TEXT;
ALTER TABLE it_system_updates ADD COLUMN it_system_name VARCHAR(64);
ALTER TABLE it_system_updates ADD COLUMN system_role_constraint_changed BIT NOT NULL DEFAULT 0;
