CREATE TABLE request_approve_postponed_constraints (
  id                                BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
  constraint_value                  TEXT NOT NULL,
  system_role_id                    BIGINT NOT NULL CONSTRAINT fk_system_role_request_approve_postponed_constraints REFERENCES system_roles(id),
  request_approve_id                BIGINT NOT NULL CONSTRAINT fk_request_approve_postponed_constraints REFERENCES request_approve(id),
  constraint_type_id                BIGINT NOT NULL CONSTRAINT fk_constraint_type_request_approve_postponed_constraints REFERENCES constraint_types(id)
);