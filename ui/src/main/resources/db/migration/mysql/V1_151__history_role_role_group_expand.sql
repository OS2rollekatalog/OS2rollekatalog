ALTER TABLE history_role_assignments MODIFY COLUMN role_role_group VARCHAR(128);
ALTER TABLE history_ou_role_assignments MODIFY COLUMN role_role_group VARCHAR(128);
ALTER TABLE history_role_assignment_excepted_users MODIFY COLUMN role_role_group VARCHAR(128);
ALTER TABLE history_role_assignment_titles MODIFY COLUMN role_role_group VARCHAR(128);
