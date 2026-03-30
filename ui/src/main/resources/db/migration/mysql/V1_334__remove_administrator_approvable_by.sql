-- Remove all occurrences of ADMINISTRATOR from approver_permission columns

-- user_roles: sole value -> empty
UPDATE user_roles SET approver_permission = '' WHERE approver_permission = 'ADMINISTRATOR';
-- user_roles: strip from comma-separated list
UPDATE user_roles SET approver_permission = REPLACE(approver_permission, ',ADMINISTRATOR', '') WHERE approver_permission LIKE '%,ADMINISTRATOR%';
UPDATE user_roles SET approver_permission = REPLACE(approver_permission, 'ADMINISTRATOR,', '') WHERE approver_permission LIKE 'ADMINISTRATOR,%';

-- rolegroup: sole value -> empty
UPDATE rolegroup SET approver_permission = '' WHERE approver_permission = 'ADMINISTRATOR';
-- rolegroup: strip from comma-separated list
UPDATE rolegroup SET approver_permission = REPLACE(approver_permission, ',ADMINISTRATOR', '') WHERE approver_permission LIKE '%,ADMINISTRATOR%';
UPDATE rolegroup SET approver_permission = REPLACE(approver_permission, 'ADMINISTRATOR,', '') WHERE approver_permission LIKE 'ADMINISTRATOR,%';

-- it_systems: sole value -> empty
UPDATE it_systems SET approver_permission = '' WHERE approver_permission = 'ADMINISTRATOR';
-- it_systems: strip from comma-separated list
UPDATE it_systems SET approver_permission = REPLACE(approver_permission, ',ADMINISTRATOR', '') WHERE approver_permission LIKE '%,ADMINISTRATOR%';
UPDATE it_systems SET approver_permission = REPLACE(approver_permission, 'ADMINISTRATOR,', '') WHERE approver_permission LIKE 'ADMINISTRATOR,%';

-- setting (global approver setting): sole value -> empty
UPDATE setting SET setting_value = '' WHERE setting_key = 'allowedrapprovers' AND setting_value = 'ADMINISTRATOR';
-- setting: strip from comma-separated list
UPDATE setting SET setting_value = REPLACE(setting_value, ',ADMINISTRATOR', '') WHERE setting_key = 'allowedrapprovers' AND setting_value LIKE '%,ADMINISTRATOR%';
UPDATE setting SET setting_value = REPLACE(setting_value, 'ADMINISTRATOR,', '') WHERE setting_key = 'allowedrapprovers' AND setting_value LIKE 'ADMINISTRATOR,%';

-- req_role_request: sole value -> empty
UPDATE req_role_request SET approver_option = '' WHERE approver_option = 'ADMINISTRATOR';
-- req_role_request: strip from comma-separated list
UPDATE req_role_request SET approver_option = REPLACE(approver_option, ',ADMINISTRATOR', '') WHERE approver_option LIKE '%,ADMINISTRATOR%';
UPDATE req_role_request SET approver_option = REPLACE(approver_option, 'ADMINISTRATOR,', '') WHERE approver_option LIKE 'ADMINISTRATOR,%';
