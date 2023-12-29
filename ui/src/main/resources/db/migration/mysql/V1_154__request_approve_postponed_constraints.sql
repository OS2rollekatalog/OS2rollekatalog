CREATE TABLE request_approve_postponed_constraints (
  id                                BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  constraint_value                  TEXT NOT NULL,
  request_approve_id                BIGINT NOT NULL,
  constraint_type_id                BIGINT NOT NULL,
  system_role_id                    BIGINT NOT NULL,

  FOREIGN KEY fk_request_approve_postponed_constraints (request_approve_id) REFERENCES request_approve(id) ON DELETE CASCADE,
  FOREIGN KEY fk_constraint_type_request_approve_postponed_constraints (constraint_type_id) REFERENCES constraint_types(id) ON DELETE CASCADE,
  FOREIGN KEY fk_system_role_request_approve_postponed_constraints (system_role_id) REFERENCES system_roles(id) ON DELETE CASCADE
);