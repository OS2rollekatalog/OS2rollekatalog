-- change one to one releation ownership
alter table user_roles
    add user_role_email_template_id bigint null;

alter table user_roles
    add constraint email
        foreign key (user_role_email_template_id) references user_role_email_template (id);

update user_roles set user_roles.user_role_email_template_id = (select id from user_role_email_template urt where user_role_id=user_roles.id);

alter table user_role_email_template
    drop foreign key fk_UserRoleEmailTemplate_user_role;
alter table user_role_email_template
    drop column user_role_id;
