ALTER TABLE user_roles ADD COLUMN can_request BOOLEAN NULL;
UPDATE user_roles SET can_request = 0;
ALTER TABLE user_roles MODIFY COLUMN can_request BOOLEAN NOT NULL DEFAULT 0;

ALTER TABLE rolegroup ADD COLUMN can_request BOOLEAN NULL;
UPDATE rolegroup SET can_request = 0;
ALTER TABLE rolegroup MODIFY COLUMN can_request BOOLEAN NOT NULL DEFAULT 0;