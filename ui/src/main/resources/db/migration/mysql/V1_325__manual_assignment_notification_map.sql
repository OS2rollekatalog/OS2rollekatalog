CREATE TABLE manual_assignment_notification_map (
    id                  BIGINT           NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_role_id        BIGINT           NOT NULL,
    user_user_id        VARCHAR(64)      NOT NULL,
    domain_id           BIGINT           NOT NULL,
    org_unit_name       VARCHAR(255)     NULL,
    assigned_by         VARCHAR(255)     NULL
);

ALTER TABLE history_role_assignments DROP COLUMN notify_by_email_if_manual_system;
ALTER TABLE user_roles_mapping DROP COLUMN notify_by_email_if_manual_system;