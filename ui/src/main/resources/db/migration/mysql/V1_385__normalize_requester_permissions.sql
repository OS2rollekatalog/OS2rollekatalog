-- INHERIT and NONE are exclusive values. Existing combinations are resolved with INHERIT winning, then NONE.
-- An empty requester permission means INHERIT.

UPDATE user_roles SET requester_permission = 'INHERIT'
WHERE requester_permission IS NULL OR TRIM(requester_permission) = '' OR FIND_IN_SET('INHERIT', REPLACE(requester_permission, ' ', '')) > 0;
UPDATE user_roles SET requester_permission = 'NONE'
WHERE FIND_IN_SET('NONE', REPLACE(requester_permission, ' ', '')) > 0 AND requester_permission <> 'NONE';

UPDATE it_systems SET requester_permission = 'INHERIT'
WHERE requester_permission IS NULL OR TRIM(requester_permission) = '' OR FIND_IN_SET('INHERIT', REPLACE(requester_permission, ' ', '')) > 0;
UPDATE it_systems SET requester_permission = 'NONE'
WHERE FIND_IN_SET('NONE', REPLACE(requester_permission, ' ', '')) > 0 AND requester_permission <> 'NONE';

UPDATE rolegroup SET requester_permission = 'INHERIT'
WHERE requester_permission IS NULL OR TRIM(requester_permission) = '' OR FIND_IN_SET('INHERIT', REPLACE(requester_permission, ' ', '')) > 0;
UPDATE rolegroup SET requester_permission = 'NONE'
WHERE FIND_IN_SET('NONE', REPLACE(requester_permission, ' ', '')) > 0 AND requester_permission <> 'NONE';

-- The global setting cannot inherit; an empty global setting means NONE.
UPDATE setting SET setting_value = TRIM(BOTH ',' FROM REGEXP_REPLACE(CONCAT(',', REPLACE(setting_value, ' ', ''), ','), ',INHERIT(?=,)', ''))
WHERE setting_key = 'allowedrequesters' AND FIND_IN_SET('INHERIT', REPLACE(setting_value, ' ', '')) > 0;
UPDATE setting SET setting_value = 'NONE'
WHERE setting_key = 'allowedrequesters' AND (setting_value IS NULL OR setting_value = '' OR FIND_IN_SET('NONE', setting_value) > 0);
