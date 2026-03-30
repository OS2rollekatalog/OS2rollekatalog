CREATE TABLE historic_it_system_assignment (
    id                           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    record_hash                  VARCHAR(255) NOT NULL,
    valid_from                   DATETIME(6)  NOT NULL,
    valid_to                     DATETIME(6)  NULL,
    it_system_id                 BIGINT       NOT NULL,
    it_system_name               VARCHAR(255) NULL,
    it_system_attestation_exempt BOOLEAN      NOT NULL DEFAULT FALSE,
    responsible_user_uuid        VARCHAR(36)  NULL,
    user_role_id                 BIGINT       NOT NULL,
    user_role_name               VARCHAR(255) NULL,
    user_role_description        TEXT         NULL,
    system_role_id               BIGINT       NOT NULL,
    system_role_name             VARCHAR(255) NULL,
    system_role_description      TEXT         NULL,

    INDEX idx_historic_it_system_assignment_temporal (valid_from, valid_to),
    INDEX idx_historic_it_system_assignment_it_system (it_system_id, valid_from, valid_to)
);

CREATE TABLE historic_it_system_assignment_constraint (
    id                                   BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    historic_it_system_assignment_id     BIGINT       NOT NULL,
    constraint_name                      VARCHAR(255) NULL,
    constraint_value_type                VARCHAR(64)  NULL,
    constraint_value                     TEXT         NULL,

    CONSTRAINT fk_historic_it_system_assignment_constraint_assignment_id
        FOREIGN KEY (historic_it_system_assignment_id)
            REFERENCES historic_it_system_assignment (id)
);
