alter table history_role_assignment_negative_titles
    add assigned_through_type varchar(64) null after role_role_group;

alter table history_role_assignment_negative_titles
    add assigned_through_uuid varchar(36) null after assigned_through_type;

alter table history_role_assignment_negative_titles
    add assigned_through_name varchar(255) null after assigned_through_uuid;

alter table history_role_assignment_negative_titles
    add inherit bit null after role_role_group_id;

