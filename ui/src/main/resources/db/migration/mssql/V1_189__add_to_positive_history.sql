alter table history_role_assignment_titles
    add assigned_through_type nvarchar(64)
    go
alter table history_role_assignment_titles
    add assigned_through_uuid nvarchar(36)
    go

alter table history_role_assignment_titles
    add assigned_through_name nvarchar(255)
    go

alter table history_role_assignment_titles
    add inherit BIT
    go

