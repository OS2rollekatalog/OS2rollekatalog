-- Role Requests Table
CREATE TABLE req_role_request (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    requester_uuid NVARCHAR(36) NOT NULL,
    approver_uuid NVARCHAR(36) NOT NULL,
    reciever_uuid NVARCHAR(36) NOT NULL,
    ou_uuid NVARCHAR(36),
    rolegroup_id BIGINT,
    user_role_id BIGINT,
    reason NVARCHAR(255),
    reject_reason NVARCHAR(255),
    role_assigner_notified BIT,
    status NVARCHAR(50) NOT NULL,
    request_timestamp DATETIME,
    status_timestamp DATETIME,
    email_sent BIT,
    request_action NVARCHAR(50),
    CONSTRAINT fk_role_request_requester FOREIGN KEY (requester_uuid) REFERENCES users(uuid),
    CONSTRAINT fk_role_request_approver FOREIGN KEY (approver_uuid) REFERENCES users(uuid),
    CONSTRAINT fk_role_request_reciever FOREIGN KEY (reciever_uuid) REFERENCES users(uuid),
    CONSTRAINT fk_role_request_org_unit FOREIGN KEY (ou_uuid) REFERENCES ous(uuid),
    CONSTRAINT fk_role_request_role_group FOREIGN KEY (rolegroup_id) REFERENCES rolegroup(id),
    CONSTRAINT fk_role_request_user_role FOREIGN KEY (user_role_id) REFERENCES user_roles(id)
);

CREATE INDEX idx_requester_uuid ON req_role_request(requester_uuid);
CREATE INDEX idx_approver_uuid ON req_role_request(approver_uuid);
CREATE INDEX idx_reciever_uuid ON req_role_request(reciever_uuid);
CREATE INDEX idx_ou_uuid ON req_role_request(ou_uuid);
CREATE INDEX idx_rolegroup_id ON req_role_request(rolegroup_id);
CREATE INDEX idx_user_role_id ON req_role_request(user_role_id);

-- Request Log Table
CREATE TABLE req_request_log (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    requester_uuid NVARCHAR(36),
    approver_uuid NVARCHAR(36),
    reciever_uuid NVARCHAR(36),
    log_id BIGINT,
    CONSTRAINT fk_request_log_requester_audit FOREIGN KEY (requester_uuid) REFERENCES users(uuid),
    CONSTRAINT fk_request_log_approver_audit FOREIGN KEY (approver_uuid) REFERENCES users(uuid),
    CONSTRAINT fk_request_log_reciever_audit FOREIGN KEY (reciever_uuid) REFERENCES users(uuid),
    CONSTRAINT fk_request_log_audit_log FOREIGN KEY (log_id) REFERENCES audit_log(id) ON DELETE CASCADE
);

CREATE INDEX idx_requester_uuid_audit ON req_request_log(requester_uuid);
CREATE INDEX idx_approver_uuid_audit ON req_request_log(approver_uuid);
CREATE INDEX idx_reciever_uuid_audit ON req_request_log(reciever_uuid);
CREATE INDEX idx_log_id ON req_request_log(log_id);

-- Request Postponed Constraint Table
CREATE TABLE req_request_postponed_constraint (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    role_request_id BIGINT,
    constraint_type_id BIGINT,
    system_role_id BIGINT,
    CONSTRAINT fk_postponed_constraint_request_id FOREIGN KEY (role_request_id) REFERENCES req_role_request(id),
    CONSTRAINT fk_postponed_constraint_constraint_type_id FOREIGN KEY (constraint_type_id) REFERENCES constraint_types(id),
    CONSTRAINT fk_postponed_constraint_system_role_id FOREIGN KEY (system_role_id) REFERENCES system_roles(id)
);
