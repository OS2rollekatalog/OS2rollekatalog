DELETE FROM user_roles_mapping WHERE user_uuid IN (SELECT uuid FROM users WHERE deleted = 1);
DELETE FROM user_rolegroups WHERE user_uuid IN (SELECT uuid FROM users WHERE deleted = 1);
DELETE FROM request_approve WHERE requester_uuid IN (SELECT uuid FROM users WHERE deleted = 1);
DELETE FROM request_approve WHERE requested_for_uuid IN (SELECT uuid FROM users WHERE deleted = 1);
DELETE FROM users WHERE deleted = 1;