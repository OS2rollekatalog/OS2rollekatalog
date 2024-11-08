ALTER TABLE history_it_systems MODIFY COLUMN it_system_name VARCHAR(256) NOT NULL;

ALTER TABLE it_system_updates MODIFY COLUMN it_system_name VARCHAR(256) NULL;