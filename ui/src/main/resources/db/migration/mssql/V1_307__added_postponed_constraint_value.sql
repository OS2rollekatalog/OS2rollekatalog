ALTER TABLE req_request_postponed_constraint ADD constraint_value TEXT NOT NULL
GO

ALTER TABLE req_role_request DROP CONSTRAINT fk_role_request_approver;
GO

ALTER TABLE req_role_request DROP COLUMN approver_uuid
GO

ALTER TABLE req_role_request ADD request_group_identifier NVARCHAR(36) NULL
GO