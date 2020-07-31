CREATE TABLE constraint_types (
    id							BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    uuid                        VARCHAR(36) NOT NULL,
    entity_id                   VARCHAR(128) NOT NULL,
    name                        VARCHAR(64) NOT NULL,
    ui_type                     VARCHAR(32) NOT NULL,
    regex                       VARCHAR(512),
    
    UNIQUE(uuid),
    UNIQUE(entity_id)
);

CREATE TABLE system_role_supported_constraints (
    system_role_id				BIGINT NOT NULL,
    constraint_type_id			BIGINT NOT NULL,
    mandatory                   BOOLEAN NOT NULL,
    
   	FOREIGN KEY (constraint_type_id) REFERENCES constraint_types(id),
   	FOREIGN KEY (system_role_id) REFERENCES system_roles(id)
);

CREATE TABLE constraint_type_value_sets (
    constraint_type_id			BIGINT NOT NULL,
    constraint_key              VARCHAR(128) NOT NULL,
    constraint_value            VARCHAR(128) NOT NULL,
    
   	FOREIGN KEY (constraint_type_id) REFERENCES constraint_types(id)
);

DROP TABLE system_role_assignment_constraint_values;

CREATE TABLE system_role_assignment_constraint_values (
	id							BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
	constraint_type_id			BIGINT NOT NULL,
	constraint_value_type		VARCHAR(64) NOT NULL,
	constraint_value			VARCHAR(4096),
	system_role_assignment_id	BIGINT NOT NULL,

	FOREIGN KEY (constraint_type_id) REFERENCES constraint_types(id),
	FOREIGN KEY (system_role_assignment_id) REFERENCES system_role_assignments(id)
);

DROP TABLE audit_log_entry;
DROP TABLE system_role_constraint_types;
