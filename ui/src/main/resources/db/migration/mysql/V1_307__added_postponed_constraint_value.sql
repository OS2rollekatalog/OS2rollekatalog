ALTER TABLE req_request_postponed_constraint ADD COLUMN constraint_value TEXT NOT NULL;

ALTER TABLE req_role_request DROP FOREIGN KEY fk_rolerequest_approver;

ALTER TABLE req_role_request DROP COLUMN approver_uuid;

ALTER TABLE req_role_request ADD COLUMN request_group_identifier VARCHAR(36) NULL;