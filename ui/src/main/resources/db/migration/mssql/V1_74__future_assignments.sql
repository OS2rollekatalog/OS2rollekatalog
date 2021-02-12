ALTER TABLE user_roles_mapping ADD start_date DATE NULL;
ALTER TABLE user_roles_mapping ADD stop_date DATE NULL;
ALTER TABLE user_roles_mapping ADD inactive BIT NOT NULL DEFAULT 0;

ALTER TABLE user_rolegroups ADD start_date DATE NULL;
ALTER TABLE user_rolegroups ADD stop_date DATE NULL;
ALTER TABLE user_rolegroups ADD inactive BIT NOT NULL DEFAULT 0;

ALTER TABLE title_roles ADD start_date DATE NULL;
ALTER TABLE title_roles ADD stop_date DATE NULL;
ALTER TABLE title_roles ADD inactive BIT NOT NULL DEFAULT 0;

ALTER TABLE title_rolegroups ADD start_date DATE NULL;
ALTER TABLE title_rolegroups ADD stop_date DATE NULL;
ALTER TABLE title_rolegroups ADD inactive BIT NOT NULL DEFAULT 0;
 
ALTER TABLE ou_roles ADD start_date DATE NULL;
ALTER TABLE ou_roles ADD stop_date DATE NULL;
ALTER TABLE ou_roles ADD inactive BIT NOT NULL DEFAULT 0;
 
ALTER TABLE ou_rolegroups ADD start_date DATE NULL;
ALTER TABLE ou_rolegroups ADD stop_date DATE NULL;
ALTER TABLE ou_rolegroups ADD inactive BIT NOT NULL DEFAULT 0;

ALTER TABLE position_roles ADD start_date DATE NULL;
ALTER TABLE position_roles ADD stop_date DATE NULL;
ALTER TABLE position_roles ADD inactive BIT NOT NULL DEFAULT 0;
 
ALTER TABLE position_rolegroups ADD start_date DATE NULL;
ALTER TABLE position_rolegroups ADD stop_date DATE NULL;
ALTER TABLE position_rolegroups ADD inactive BIT NOT NULL DEFAULT 0;
