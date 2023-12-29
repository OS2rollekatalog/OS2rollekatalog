ALTER TABLE user_roles_mapping ADD COLUMN stop_date_user VARCHAR(255) NULL;
ALTER TABLE user_rolegroups ADD COLUMN stop_date_user VARCHAR(255) NULL;
ALTER TABLE ou_roles ADD COLUMN stop_date_user VARCHAR(255) NULL;
ALTER TABLE ou_rolegroups ADD COLUMN stop_date_user VARCHAR(255) NULL;
ALTER TABLE position_roles ADD COLUMN stop_date_user VARCHAR(255) NULL;
ALTER TABLE position_rolegroups ADD COLUMN stop_date_user VARCHAR(255) NULL;