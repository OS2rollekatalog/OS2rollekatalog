alter table system_roles
    alter column name nvarchar(255) not null
    go

alter table history_system_roles
    alter column system_role_name nvarchar(255) not null
    go

alter table history_user_roles_system_roles
    alter column system_role_name nvarchar(255) not null
    go

