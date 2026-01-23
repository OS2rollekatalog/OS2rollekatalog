
alter table user_roles
    modify requester_permission varchar(1024) default 'INHERIT' not null;
alter table user_roles
    modify approver_permission varchar(1024) default 'INHERIT' not null;

update user_roles set approver_permission='AUTHORIZED,MANAGERORSUBSTITUTE,AUTHRESPONSIBLE,ADMINISTRATOR' where approver_permission='AUTHORIZEDMANAGERORAUTHRESPONSIBLE';
update user_roles set approver_permission='MANAGERORSUBSTITUTE,AUTHRESPONSIBLE,ADMINISTRATOR' where approver_permission='MANAGERORAUTHRESPONSIBLE';
update user_roles set approver_permission='AUTHORIZED,ADMINISTRATOR' where approver_permission='AUTHORIZEDONLY';
update user_roles set approver_permission='ADMINISTRATOR' where approver_permission='ADMINONLY';
update user_roles set approver_permission='SYSTEMRESPONSIBLE,ADMINISTRATOR' where approver_permission='SYSTEMRESPONSIBLE';

update user_roles set requester_permission='EMPLOYEE,ADMIN' where requester_permission='EMPLOYEESONLY';
update user_roles set requester_permission='EMPLOYEE,AUTHRESPONSIBLE,AUTHORIZED,MANAGERORSUBSTITUTE,ADMIN' where requester_permission='ALL';
update user_roles set requester_permission='EMPLOYEE,AUTHRESPONSIBLE,MANAGERORSUBSTITUTE,ADMIN' where requester_permission='EMPLOYEESMANAGERSANDAUTHRESPONSIBLE';
update user_roles set requester_permission='AUTHORIZED,AUTHRESPONSIBLE,MANAGERORSUBSTITUTE,ADMIN' where requester_permission='AUTHORIZEDMANAGERSANDAUTHRESPONSIBLE';
update user_roles set requester_permission='AUTHORIZED,ADMIN' where requester_permission='AUTHORIZEDONLY';
update user_roles set requester_permission='MANAGERORSUBSTITUTE,AUTHRESPONSIBLE' where requester_permission='MANAGERANDAUTHRESPONSIBLE';

UPDATE setting SET setting_value = 'AUTHORIZED,MANAGERORSUBSTITUTE,AUTHRESPONSIBLE,ADMINISTRATOR' WHERE setting_key = 'allowedrapprovers' and setting_value='AUTHORIZEDMANAGERORAUTHRESPONSIBLE';
UPDATE setting SET setting_value = 'MANAGERORSUBSTITUTE,AUTHRESPONSIBLE,ADMINISTRATOR' WHERE setting_key = 'allowedrapprovers' and setting_value='MANAGERORAUTHRESPONSIBLE';
UPDATE setting SET setting_value = 'AUTHORIZED,ADMINISTRATOR' WHERE setting_key = 'allowedrapprovers' and setting_value='AUTHORIZEDONLY';
UPDATE setting SET setting_value = 'ADMINISTRATOR' WHERE setting_key = 'allowedrapprovers' and setting_value='ADMINONLY';
UPDATE setting SET setting_value = 'SYSTEMRESPONSIBLE,ADMINISTRATOR' WHERE setting_key = 'allowedrapprovers' and setting_value='SYSTEMRESPONSIBLE';

UPDATE setting SET setting_value = 'EMPLOYEE,ADMIN' WHERE setting_key = 'allowedrequesters' and setting_value='EMPLOYEESONLY';
UPDATE setting SET setting_value = 'EMPLOYEE,AUTHRESPONSIBLE,AUTHORIZED,MANAGERORSUBSTITUTE,ADMIN' WHERE setting_key = 'allowedrequesters' and setting_value='ALL';
UPDATE setting SET setting_value = 'EMPLOYEE,AUTHRESPONSIBLE,MANAGERORSUBSTITUTE,ADMIN' WHERE setting_key = 'allowedrequesters' and setting_value='EMPLOYEESMANAGERSANDAUTHRESPONSIBLE';
UPDATE setting SET setting_value = 'AUTHORIZED,AUTHRESPONSIBLE,MANAGERORSUBSTITUTE,ADMIN' WHERE setting_key = 'allowedrequesters' and setting_value='AUTHORIZEDMANAGERSANDAUTHRESPONSIBLE';
UPDATE setting SET setting_value = 'AUTHORIZED,ADMIN' WHERE setting_key = 'allowedrequesters' and setting_value='AUTHORIZEDONLY';
UPDATE setting SET setting_value = 'MANAGERORSUBSTITUTE,AUTHRESPONSIBLE' WHERE setting_key = 'allowedrequesters' and setting_value='MANAGERANDAUTHRESPONSIBLE';
