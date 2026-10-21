-- INHERIT and AUTOMATIC are exclusive values. Existing combinations are resolved with INHERIT winning, then AUTOMATIC.
-- NULL is the column default and means INHERIT; an empty approver permission means nobody (only admins).

UPDATE user_roles SET approver_permission = 'INHERIT'
WHERE approver_permission IS NULL OR FIND_IN_SET('INHERIT', REPLACE(approver_permission, ' ', '')) > 0;
UPDATE user_roles SET approver_permission = 'AUTOMATIC'
WHERE FIND_IN_SET('AUTOMATIC', REPLACE(approver_permission, ' ', '')) > 0 AND approver_permission <> 'AUTOMATIC';

UPDATE it_systems SET approver_permission = 'INHERIT'
WHERE approver_permission IS NULL OR FIND_IN_SET('INHERIT', REPLACE(approver_permission, ' ', '')) > 0;
UPDATE it_systems SET approver_permission = 'AUTOMATIC'
WHERE FIND_IN_SET('AUTOMATIC', REPLACE(approver_permission, ' ', '')) > 0 AND approver_permission <> 'AUTOMATIC';

UPDATE rolegroup SET approver_permission = 'INHERIT'
WHERE approver_permission IS NULL OR FIND_IN_SET('INHERIT', REPLACE(approver_permission, ' ', '')) > 0;
UPDATE rolegroup SET approver_permission = 'AUTOMATIC'
WHERE FIND_IN_SET('AUTOMATIC', REPLACE(approver_permission, ' ', '')) > 0 AND approver_permission <> 'AUTOMATIC';

-- The global setting cannot inherit.
UPDATE setting SET setting_value = TRIM(BOTH ',' FROM REGEXP_REPLACE(CONCAT(',', REPLACE(setting_value, ' ', ''), ','), ',INHERIT(?=,)', ''))
WHERE setting_key = 'allowedrapprovers' AND FIND_IN_SET('INHERIT', REPLACE(setting_value, ' ', '')) > 0;
UPDATE setting SET setting_value = 'AUTOMATIC'
WHERE setting_key = 'allowedrapprovers' AND FIND_IN_SET('AUTOMATIC', setting_value) > 0 AND setting_value <> 'AUTOMATIC';
