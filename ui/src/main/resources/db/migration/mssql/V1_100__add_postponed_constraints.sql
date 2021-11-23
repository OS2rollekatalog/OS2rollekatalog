CREATE TABLE postponed_constraints (
  id                                BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1),
  constraint_value                  TEXT NOT NULL,
  system_role_id                    BIGINT NOT NULL CONSTRAINT fk_system_role_postponed_constraints REFERENCES system_roles(id), -- sql server doesn't support multiple cascade paths. Hence no cascade rule here
  user_user_role_assignment_id      BIGINT NOT NULL CONSTRAINT fk_user_user_role_assignment_postponed_constraints REFERENCES user_roles_mapping(id) ON DELETE CASCADE,
  constraint_type_id                BIGINT NOT NULL CONSTRAINT fk_constraint_type_postponed_constraints REFERENCES constraint_types(id) ON DELETE CASCADE
);