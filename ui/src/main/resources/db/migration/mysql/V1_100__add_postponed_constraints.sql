CREATE TABLE postponed_constraints (
  id                                BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  constraint_value                  TEXT NOT NULL,
  user_user_role_assignment_id      BIGINT NOT NULL,
  constraint_type_id                BIGINT NOT NULL,
  system_role_id                    BIGINT NOT NULL,

  FOREIGN KEY fk_user_user_role_assignment_postponed_constraints (user_user_role_assignment_id) REFERENCES user_roles_mapping(id) ON DELETE CASCADE,
  FOREIGN KEY fk_constraint_type_postponed_constraints (constraint_type_id) REFERENCES constraint_types(id) ON DELETE CASCADE,
  FOREIGN KEY fk_system_role_postponed_constraints (system_role_id) REFERENCES system_roles(id) ON DELETE CASCADE
);