ALTER TABLE history_role_assignments ALTER COLUMN role_role_group NVARCHAR(128);
ALTER TABLE history_ou_role_assignments ALTER COLUMN role_role_group NVARCHAR(128);
ALTER TABLE history_role_assignment_excepted_users ALTER COLUMN role_role_group NVARCHAR(128);
ALTER TABLE history_role_assignment_titles ALTER COLUMN role_role_group NVARCHAR(128);
