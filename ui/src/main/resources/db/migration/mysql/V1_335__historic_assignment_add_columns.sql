alter table historic_assignment
    add responsible_user_uuid varchar(36) null;

CREATE TABLE historic_ou_assignment
(
    id                           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    record_hash                  VARCHAR(255) NOT NULL,
    valid_from                   DATETIME(6)  NOT NULL,
    valid_to                     DATETIME(6)  NULL,
    ou_uuid                      VARCHAR(36)  NOT NULL,
    ou_name                      VARCHAR(255) NULL,
    it_system_id                 BIGINT       NULL,
    it_system_name               VARCHAR(255) NULL,
    role_id                      BIGINT       NULL,
    role_name                    VARCHAR(255) NULL,
    role_description             TEXT         NULL,
    role_role_group_id           BIGINT       NULL,
    role_role_group_name         VARCHAR(255) NULL,
    role_group_description       TEXT         NULL,
    sensitive_role               BOOLEAN      NOT NULL DEFAULT FALSE,
    extra_sensitive_role         BOOLEAN      NOT NULL DEFAULT FALSE,
    responsible_user_uuid        VARCHAR(36)  NULL,
    it_system_attestation_exempt BOOLEAN      NOT NULL DEFAULT FALSE,
    assigned_through_type        VARCHAR(50)  NULL,
    assigned_through_uuid        VARCHAR(36)  NULL,
    assigned_through_name        VARCHAR(255) NULL,
    assigned_when                DATETIME(6)  NULL,
    applies_only_to_manager      BOOLEAN      NOT NULL DEFAULT FALSE,
    applies_also_to_substitutes  BOOLEAN      NOT NULL DEFAULT FALSE,
    inherit_to_children          BOOLEAN      NOT NULL DEFAULT FALSE,

    INDEX idx_historic_ou_assignment_temporal (valid_from, valid_to),
    INDEX idx_historic_ou_assignment_ou (ou_uuid, valid_from, valid_to)
);

CREATE TABLE historic_ou_assignment_exclusion
(
    id                        BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    historic_ou_assignment_id BIGINT      NOT NULL,
    exclusion_type            VARCHAR(50) NOT NULL,
    uuids                     TEXT        NULL,

    CONSTRAINT fk_historic_ou_assignment_exclusion_assignment_id
        FOREIGN KEY (historic_ou_assignment_id) REFERENCES historic_ou_assignment (id)
);

ALTER TABLE attestation_user_role_assignments
    DROP COLUMN manager;