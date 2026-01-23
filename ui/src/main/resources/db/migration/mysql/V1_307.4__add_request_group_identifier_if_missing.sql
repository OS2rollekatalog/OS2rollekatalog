ALTER TABLE req_role_request
    ADD COLUMN IF NOT EXISTS request_group_identifier VARCHAR(36) NULL;