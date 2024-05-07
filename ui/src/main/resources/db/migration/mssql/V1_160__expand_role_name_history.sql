
alter table history_role_assignment_excepted_users
    alter COLUMN role_name NVARCHAR(128) not null;
