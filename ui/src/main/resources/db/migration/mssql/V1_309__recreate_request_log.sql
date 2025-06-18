IF OBJECT_ID('req_request_log', 'U') IS NOT NULL
    DROP TABLE req_request_log;

-- Request Log Table
CREATE TABLE req_request_log (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    request_timestamp SMALLDATETIME NOT NULL,
    acting_user_uuid NVARCHAR(36) NOT NULL,
    target_user_uuid NVARCHAR(36) NOT NULL,
    request_event NVARCHAR(50) NOT NULL,
    user_role_id BIGINT NULL,
    rolegroup_id BIGINT NULL,
    details TEXT NULL,
    CONSTRAINT fk_request_log_acting_user_uuid FOREIGN KEY (acting_user_uuid) REFERENCES users(uuid),
    CONSTRAINT fk_request_log_target_user_uuid FOREIGN KEY (target_user_uuid) REFERENCES users(uuid),
    CONSTRAINT fk_request_log_rolegroup_id FOREIGN KEY (rolegroup_id) REFERENCES rolegroup(id),
    CONSTRAINT fk_request_log_user_role_id FOREIGN KEY (user_role_id) REFERENCES user_roles(id)
);
