
alter table history_role_assignments
    add start_date date null;
alter table history_role_assignments
    add stop_date date null;

alter table history_role_assignment_titles
    add start_date date null;
alter table history_role_assignment_titles
    add stop_date date null;

alter table history_role_assignment_excepted_users
    add start_date date null;
alter table history_role_assignment_excepted_users
    add stop_date date null;

alter table history_ou_role_assignments
    add start_date date null;
alter table history_ou_role_assignments
    add stop_date date null;

alter table history_ou_kle_assignments
    add start_date date null;
alter table history_ou_kle_assignments
    add stop_date date null;

