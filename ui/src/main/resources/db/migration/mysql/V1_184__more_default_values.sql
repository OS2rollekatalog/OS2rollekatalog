alter table user_roles_mapping
    alter column assigned_by_user_id set default 'System';

alter table user_roles_mapping
    alter column assigned_by_name set default 'System';

alter table user_rolegroups
    alter column assigned_by_user_id set default 'System';

alter table user_rolegroups
    alter column assigned_by_name set default 'System';

alter table ou_roles
    alter column assigned_by_user_id set default 'System';

alter table ou_roles
    alter column assigned_by_name set default 'System';

alter table ou_rolegroups
    alter column assigned_by_user_id set default 'System';

alter table ou_rolegroups
    alter column assigned_by_name set default 'System';

