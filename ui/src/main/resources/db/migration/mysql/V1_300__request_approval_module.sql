-- Role Requests Table
CREATE TABLE req_role_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    requester_uuid VARCHAR(36) NOT NULL,
    approver_uuid VARCHAR(36) NOT NULL,
    reciever_uuid VARCHAR(36) NOT NULL,
    ou_uuid VARCHAR(36),
    rolegroup_id BIGINT,
    user_role_id BIGINT,
    reason VARCHAR(255),
    reject_reason VARCHAR(255),
    role_assigner_notified TINYINT(1),
    status VARCHAR(50) NOT NULL,
    request_timestamp DATETIME,
    status_timestamp DATETIME,
    email_sent TINYINT(1),
    request_action VARCHAR(50),
    CONSTRAINT fk_rolerequest_requester FOREIGN KEY (requester_uuid) REFERENCES users(uuid) ON DELETE CASCADE,
    CONSTRAINT fk_rolerequest_approver FOREIGN KEY (approver_uuid) REFERENCES users(uuid) ON DELETE CASCADE,
    CONSTRAINT fk_rolerequest_reciever FOREIGN KEY (reciever_uuid) REFERENCES users(uuid) ON DELETE CASCADE,
    CONSTRAINT fk_rolerequest_org_unit FOREIGN KEY (ou_uuid) REFERENCES ous(uuid) ON DELETE CASCADE,
    CONSTRAINT fk_rolerequest_role_group FOREIGN KEY (rolegroup_id) REFERENCES rolegroup(id) ON DELETE CASCADE,
    CONSTRAINT fk_rolerequest_user_role FOREIGN KEY (user_role_id) REFERENCES user_roles(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Request Log Table
CREATE TABLE req_request_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    requester_uuid VARCHAR(36),
    approver_uuid VARCHAR(36),
    reciever_uuid VARCHAR(36),
    log_id BIGINT,
    CONSTRAINT fk_requester_audit FOREIGN KEY (requester_uuid) REFERENCES users(uuid) ON DELETE CASCADE,
    CONSTRAINT fk_approver_audit FOREIGN KEY (approver_uuid) REFERENCES users(uuid) ON DELETE CASCADE,
    CONSTRAINT fk_reciever_audit FOREIGN KEY (reciever_uuid) REFERENCES users(uuid) ON DELETE CASCADE,
    CONSTRAINT fk_log FOREIGN KEY (log_id) REFERENCES audit_log(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Request postponed constraint table
CREATE TABLE req_request_postponed_constraint (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_request_id BIGINT,
    constraint_type_id BIGINT,
    system_role_id BIGINT,
    CONSTRAINT fk_postponed_constraint_request_id FOREIGN KEY (role_request_id) REFERENCES req_role_request(id) ON DELETE CASCADE,
    CONSTRAINT fk_postponed_constraint_constraint_type_id FOREIGN KEY (constraint_type_id) REFERENCES constraint_types(id) ON DELETE CASCADE,
    CONSTRAINT fk_postponed_constraint_system_role_id FOREIGN KEY (system_role_id) REFERENCES system_roles(id) ON DELETE CASCADE
) ENGINE=InnoDB;
