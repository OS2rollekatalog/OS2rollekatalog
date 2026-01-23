ALTER TABLE user_roles
ADD COLUMN system_role_link_type VARCHAR(128);

UPDATE user_roles
SET system_role_link_type = 'NONE'
WHERE linked_system_role IS NULL;

UPDATE user_roles
SET system_role_link_type = 'NAME_AND_DESCRIPTION'
WHERE linked_system_role IS NOT NULL;

ALTER TABLE user_roles
MODIFY COLUMN system_role_link_type VARCHAR(128) NOT NULL;