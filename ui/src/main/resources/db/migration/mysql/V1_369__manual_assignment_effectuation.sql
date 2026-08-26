CREATE TABLE manual_assignment_effectuation (
    id                     BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_uuid              VARCHAR(36)  NOT NULL,
    user_role_id           BIGINT       NULL,
    it_system_id           BIGINT       NOT NULL,
    operation              VARCHAR(16)  NOT NULL,
    status                 VARCHAR(16)  NOT NULL,
    created_at             DATETIME(6)  NOT NULL,
    completed_at           DATETIME(6)  NULL,
    completed_by_user_uuid VARCHAR(36)  NULL,
    comment                TEXT         NULL,
    email_sent             BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_manual_assignment_effectuation_user
        FOREIGN KEY (user_uuid) REFERENCES users (uuid) ON DELETE CASCADE,
    CONSTRAINT fk_manual_assignment_effectuation_user_role
        FOREIGN KEY (user_role_id) REFERENCES user_roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_manual_assignment_effectuation_it_system
        FOREIGN KEY (it_system_id) REFERENCES it_systems (id) ON DELETE CASCADE,

    INDEX idx_manual_assignment_effectuation_status_itsystem (status, it_system_id),
    INDEX idx_manual_assignment_effectuation_user_role (user_uuid, user_role_id),
    INDEX idx_manual_assignment_effectuation_email_sent (status, email_sent)
);

ALTER TABLE it_systems ADD COLUMN manual_effectuation_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE manual_welcome_email_templates (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    it_system_id BIGINT       NOT NULL,
    title        VARCHAR(255) NOT NULL,
    message      TEXT         NOT NULL,
    notes        TEXT         NULL,
    enabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    operation    VARCHAR(16)  NOT NULL,

    CONSTRAINT fk_manual_welcome_email_templates_it_system FOREIGN KEY (it_system_id) REFERENCES it_systems (id) ON DELETE CASCADE,
    CONSTRAINT uq_manual_welcome_email_templates_it_system_operation UNIQUE (it_system_id, operation)
);
