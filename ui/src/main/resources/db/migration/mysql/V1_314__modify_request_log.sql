ALTER TABLE req_request_log ADD COLUMN acting_username VARCHAR(255) NULL;
ALTER TABLE req_request_log ADD COLUMN target_username VARCHAR(255) NULL;
ALTER TABLE req_request_log ADD COLUMN role_name VARCHAR(255) NULL;
ALTER TABLE req_request_log ADD COLUMN rolegroup_name VARCHAR(255) NULL;

UPDATE req_request_log
    SET acting_username = (SELECT name FROM users WHERE uuid = req_request_log.acting_user_uuid);

UPDATE req_request_log
    SET target_username = (SELECT name FROM users WHERE uuid = req_request_log.target_user_uuid);

UPDATE req_request_log
    SET role_name = (SELECT name FROM user_roles WHERE id = req_request_log.user_role_id)
WHERE user_role_id IS NOT NULL;

UPDATE req_request_log
    SET rolegroup_name = (SELECT name FROM rolegroup WHERE id = req_request_log.rolegroup_id)
WHERE rolegroup_id IS NOT NULL;

ALTER TABLE req_request_log DROP FOREIGN KEY fk_request_log_acting_user_uuid;
ALTER TABLE req_request_log DROP FOREIGN KEY fk_request_log_target_user_uuid;
ALTER TABLE req_request_log DROP FOREIGN KEY fk_request_log_rolegroup_id;
ALTER TABLE req_request_log DROP FOREIGN KEY fk_request_log_user_role_id;