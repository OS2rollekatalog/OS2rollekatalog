
ALTER TABLE req_role_request
    MODIFY reason VARCHAR(768) NULL,
    MODIFY reject_reason VARCHAR(768) NULL;
