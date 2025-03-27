alter table user_roles_mapping
    add default 'System' for assigned_by_user_id
    go

alter table user_roles_mapping
    add default 'System' for assigned_by_name
    go

alter table user_rolegroups
    add default 'System' for assigned_by_user_id
    go

alter table user_rolegroups
    add default 'System' for assigned_by_name
    go

alter table ou_roles
    add default 'System' for assigned_by_user_id
    go

alter table ou_roles
    add default 'System' for assigned_by_name
    go

alter table ou_rolegroups
    add default 'System' for assigned_by_user_id
    go

alter table ou_rolegroups
    add default 'System' for assigned_by_name
    go


