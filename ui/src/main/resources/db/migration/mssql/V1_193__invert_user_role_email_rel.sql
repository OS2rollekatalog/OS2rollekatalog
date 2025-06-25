-- change one to one releation ownership
alter table user_roles
    add user_role_email_template_id bigint
    constraint fk_user_email_template
            references user_role_email_template
go

update user_roles set user_roles.user_role_email_template_id = (select id from user_role_email_template urt where user_role_id=user_roles.id);

alter table dbo.user_role_email_template
    drop constraint fk_UserRoleEmailTemplate_user_role
    go

alter table dbo.user_role_email_template
    drop column user_role_id
    go
