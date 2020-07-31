CREATE TABLE constraint_types (
    id                          BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
    uuid                        NVARCHAR(36) NOT NULL UNIQUE,
    entity_id                   NVARCHAR(128) NOT NULL UNIQUE,
    name                        NVARCHAR(64) NOT NULL,
    ui_type                     NVARCHAR(32) NOT NULL,
    regex                       NVARCHAR(512)
);

CREATE TABLE system_role_supported_constraints (
    system_role_id				BIGINT NOT NULL FOREIGN KEY REFERENCES system_roles(id),
    constraint_type_id			BIGINT NOT NULL FOREIGN KEY REFERENCES constraint_types(id),
    mandatory                   SMALLINT NOT NULL
);

CREATE TABLE constraint_type_value_sets (
    constraint_type_id			BIGINT NOT NULL	FOREIGN KEY REFERENCES constraint_types(id),
    constraint_key              NVARCHAR(128) NOT NULL,
    constraint_value            NVARCHAR(128) NOT NULL
);

DROP TABLE system_role_assignment_constraint_values;

CREATE TABLE system_role_assignment_constraint_values (
    id                          BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
	constraint_type_id			BIGINT NOT NULL	FOREIGN KEY REFERENCES constraint_types(id),
	constraint_value_type		NVARCHAR(64) NOT NULL,
	constraint_value			NVARCHAR(4000),
	system_role_assignment_id	BIGINT NOT NULL FOREIGN KEY REFERENCES system_role_assignments(id)
);

DROP TABLE audit_log_entry;
DROP TABLE system_role_constraint_types;
