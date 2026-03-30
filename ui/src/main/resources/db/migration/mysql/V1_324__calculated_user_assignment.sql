# Holds a users currently assigned userroles from all sources
CREATE TABLE current_assignment
(
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_hash              VARCHAR(255) NOT NULL,
    created_at               DATETIME     NOT NULL,
    updated_at               DATETIME     NULL,
    start_date               DATE         NULL,
    stop_date                DATE         NULL,
    assignment_id            BIGINT       NOT NULL,
    case_number              VARCHAR(255) NULL,
    assigned_by              VARCHAR(255) NULL,
    assignment_user_uuid     VARCHAR(36)  NULL,
    assignment_user_role_id  BIGINT       NULL,
    assignment_it_system_id  BIGINT       NULL,
    assignment_role_group_id BIGINT       NULL,
    assignment_ou_uuid       VARCHAR(36)  NULL,
    assignment_title_uuid    VARCHAR(36)  NULL,
    responsible_ou_uuid      VARCHAR(36)  NULL,

    CONSTRAINT fk_current_assignment_user_uuid FOREIGN KEY (assignment_user_uuid)
        REFERENCES users (uuid) ON DELETE SET NULL,
    CONSTRAINT fk_current_assignment_user_role_id FOREIGN KEY (assignment_user_role_id)
        REFERENCES user_roles (id) ON DELETE SET NULL,
    CONSTRAINT fk_current_assignment_it_system_id FOREIGN KEY (assignment_it_system_id)
        REFERENCES it_systems (id) ON DELETE SET NULL,
    CONSTRAINT fk_current_assignment_role_group_id FOREIGN KEY (assignment_role_group_id)
        REFERENCES rolegroup (id) ON DELETE SET NULL,
    CONSTRAINT fk_current_assignment_ou_uuid FOREIGN KEY (assignment_ou_uuid)
        REFERENCES ous (uuid) ON DELETE SET NULL,
    CONSTRAINT fk_current_assignment_title_uuid FOREIGN KEY (assignment_title_uuid)
        REFERENCES titles (uuid) ON DELETE SET NULL,
    CONSTRAINT fk_current_assignment_responsible_ou_uuid FOREIGN KEY (responsible_ou_uuid)
        REFERENCES ous (uuid) ON DELETE SET NULL
);

CREATE INDEX idx_current_assignment_user_itsystem ON current_assignment (assignment_user_uuid, assignment_it_system_id);
CREATE INDEX idx_current_assignment_userrole_itsystem ON current_assignment (assignment_user_role_id, assignment_it_system_id);
CREATE INDEX idx_current_assignment_itsystem_userrole ON current_assignment (assignment_it_system_id, assignment_user_role_id);
CREATE INDEX idx_current_assignment_ou ON current_assignment (assignment_ou_uuid);
CREATE INDEX idx_current_assignment_responsible_ou ON current_assignment (responsible_ou_uuid);
CREATE INDEX idx_current_assignment_user_dates ON current_assignment (assignment_user_uuid, start_date, stop_date);

# holds postponed constraints for calculated assignments
CREATE TABLE current_assignment_postponed_constraint
(
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    current_assignment_id     BIGINT       NOT NULL,
    constraint_type_id        BIGINT       NOT NULL,
    constraint_type_uuid      VARCHAR(36)  NOT NULL,
    constraint_type_name      VARCHAR(255) NOT NULL,
    constraint_type_entity_id VARCHAR(255) NOT NULL,
    constraint_type_ui_type   VARCHAR(255) NOT NULL,
    system_role_id            BIGINT       NOT NULL,
    value                     TEXT         NOT NULL,

    CONSTRAINT fk_current_assignment_postponed_constraint_current_assignment_id FOREIGN KEY (current_assignment_id)
        REFERENCES current_assignment (id) ON DELETE CASCADE
);

# Holds specific exceptions to OU assignments
CREATE TABLE current_excepted_assignment
(
    id                                BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_hash                       VARCHAR(255) NOT NULL,
    exception_user_uuid               VARCHAR(36)  NOT NULL,
    exception_user_role_id            BIGINT       NOT NULL,
    exception_user_role_name          VARCHAR(255) NOT NULL,
    exception_user_role_description   VARCHAR(255) NOT NULL,
    exception_role_group_id           BIGINT       NULL,
    exception_role_group_name         VARCHAR(255) NULL,
    exception_role_group_description  VARCHAR(255) NULL,
    exception_it_system_id            BIGINT       NOT NULL,
    exception_it_system_name          VARCHAR(255) NOT NULL,
    exception_ou_uuid                 VARCHAR(36)  NULL,
    exception_ou_name                 VARCHAR(255) NULL,
    exception_title_uuid              VARCHAR(36)  NULL,
    exception_title_name              VARCHAR(255) NULL,
    exception_assignment_id           BIGINT       NOT NULL,
    responsible_ou_uuid               VARCHAR(36)  NULL,
    responsible_ou_name               VARCHAR(255) NULL,
    assigned_by                       VARCHAR(255) NULL,
    start_date                        DATE         NULL,
    stop_date                         DATE         NULL,
    created_at                        DATETIME     NOT NULL

);

# Holds a users historic calculated assignments from all sources
CREATE TABLE historic_assignment
(
    id                            BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_hash                   VARCHAR(255)     NOT NULL,
    updated_at                    DATETIME         NULL,
    start_date                    DATE             NULL,
    stop_date                     DATE             NULL,
    valid_from                    DATETIME         NOT NULL,
    valid_to                      DATETIME         NULL,

    user_uuid                     VARCHAR(36)      NULL,
    user_id                       VARCHAR(64)      NULL,
    user_name                     VARCHAR(255)     NULL,

    user_role_id                  BIGINT           NULL,
    user_role_name                VARCHAR(255)     NULL,
    user_role_description         TEXT             NULL,
    sensitive_role                bit default b'0' NOT NULL,
    extra_sensitive_role          bit default b'0' NOT NULL,

    it_system_id                  BIGINT           NULL,
    it_system_name                VARCHAR(255)     NULL,

    role_group_id                 BIGINT           NULL,
    role_group_name               VARCHAR(255)     NULL,
    role_group_description        TEXT             NULL,

    assigned_by                   VARCHAR(255)     NULL,
    assigned_through_type         VARCHAR(255)     NULL,
    assigned_through_ou_uuid      VARCHAR(36)      NULL,
    assigned_through_ou_name      VARCHAR(255)     NULL,
    assigned_through_title_uuid   VARCHAR(36)      NULL,
    assigned_through_title_name   VARCHAR(255)     NULL,
    assigned_through_rg_id        BIGINT           NULL,
    assigned_through_rg_name      VARCHAR(255)     NULL,

    responsible_ou_uuid           VARCHAR(36)      NULL,
    responsible_ou_name           VARCHAR(255)     NULL
);

create index idx_historic_assignment_temporal
    on historic_assignment (valid_from, valid_to);

# holds postponed constrains for current calculated roles
CREATE TABLE historic_assignment_constraint
(
    id                        BIGINT auto_increment primary key,
    historic_assignment_id    BIGINT       NOT NULL,
    constraint_type_uuid      VARCHAR(36)  NOT NULL,
    constraint_type_name      VARCHAR(255) NOT NULL,
    constraint_type_entity_id VARCHAR(255) NOT NULL,
    value                     TEXT         NOT NULL,

    CONSTRAINT fk_historic_assignment_constraint_historic_assignment_id FOREIGN KEY (historic_assignment_id)
        REFERENCES historic_assignment (id) ON DELETE CASCADE
);

# Holds specific historic exceptions to OU assignments
CREATE TABLE historic_excepted_assignment
(
    id                                BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_hash                       VARCHAR(255) NOT NULL,
    exception_user_uuid               VARCHAR(36)  NOT NULL,
    exception_user_role_id            BIGINT       NOT NULL,
    exception_user_role_name          VARCHAR(255) NOT NULL,
    exception_user_role_description   VARCHAR(255) NOT NULL,
    exception_role_group_id           BIGINT       NULL,
    exception_role_group_name         VARCHAR(255) NULL,
    exception_role_group_description  VARCHAR(255) NULL,
    exception_it_system_id            BIGINT       NOT NULL,
    exception_it_system_name          VARCHAR(255) NOT NULL,
    exception_ou_uuid                 VARCHAR(36)  NULL,
    exception_ou_name                 VARCHAR(255) NULL,
    exception_title_uuid              VARCHAR(36)  NULL,
    exception_title_name              VARCHAR(255) NULL,
    exception_assignment_id           BIGINT       NOT NULL,
    responsible_ou_uuid               VARCHAR(36)  NULL,
    responsible_ou_name               VARCHAR(255) NULL,
    assigned_by                       VARCHAR(255) NULL,
    start_date                        DATE         NULL,
    stop_date                         DATE         NULL,
    valid_from                        DATETIME     NOT NULL,
    valid_to                          DATETIME     NULL
);

