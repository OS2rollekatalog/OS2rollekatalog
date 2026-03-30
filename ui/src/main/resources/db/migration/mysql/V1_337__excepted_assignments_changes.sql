alter table current_excepted_assignment
    modify exception_user_role_description text null;

alter table current_excepted_assignment
    modify exception_role_group_description text null;

alter table historic_excepted_assignment
    modify exception_user_role_description text null;

alter table historic_excepted_assignment
    modify exception_role_group_description text null;

