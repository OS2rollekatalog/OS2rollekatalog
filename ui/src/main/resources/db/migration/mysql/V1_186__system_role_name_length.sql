alter table system_roles
    modify name varchar(255) not null;
alter table history_system_roles
    modify system_role_name varchar(255) not null;
alter table history_user_roles_system_roles
    modify system_role_name varchar(255) null;
