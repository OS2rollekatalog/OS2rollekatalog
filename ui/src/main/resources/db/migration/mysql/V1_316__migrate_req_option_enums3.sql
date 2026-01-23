
alter table it_systems
    modify requester_permission varchar(1024) default 'INHERIT';
alter table it_systems
    modify approver_permission varchar(1024) default 'INHERIT';

update it_systems set approver_permission='AUTHORIZED,MANAGERORSUBSTITUTE,AUTHRESPONSIBLE,ADMINISTRATOR' where approver_permission='AUTHORIZEDMANAGERORAUTHRESPONSIBLE';
update it_systems set approver_permission='MANAGERORSUBSTITUTE,AUTHRESPONSIBLE,ADMINISTRATOR' where approver_permission='MANAGERORAUTHRESPONSIBLE';
update it_systems set approver_permission='AUTHORIZED,ADMINISTRATOR' where approver_permission='AUTHORIZEDONLY';
update it_systems set approver_permission='ADMINISTRATOR' where approver_permission='ADMINONLY';
update it_systems set approver_permission='SYSTEMRESPONSIBLE,ADMINISTRATOR' where approver_permission='SYSTEMRESPONSIBLE';
update it_systems set approver_permission='INHERIT' where approver_permission IS NULL;

update it_systems set requester_permission='EMPLOYEE,ADMIN' where requester_permission='EMPLOYEESONLY';
update it_systems set requester_permission='EMPLOYEE,AUTHRESPONSIBLE,AUTHORIZED,MANAGERORSUBSTITUTE,ADMIN' where requester_permission='ALL';
update it_systems set requester_permission='EMPLOYEE,AUTHRESPONSIBLE,MANAGERORSUBSTITUTE,ADMIN' where requester_permission='EMPLOYEESMANAGERSANDAUTHRESPONSIBLE';
update it_systems set requester_permission='AUTHORIZED,AUTHRESPONSIBLE,MANAGERORSUBSTITUTE,ADMIN' where requester_permission='AUTHORIZEDMANAGERSANDAUTHRESPONSIBLE';
update it_systems set requester_permission='AUTHORIZED,ADMIN' where requester_permission='AUTHORIZEDONLY';
update it_systems set requester_permission='MANAGERORSUBSTITUTE,AUTHRESPONSIBLE' where requester_permission='MANAGERANDAUTHRESPONSIBLE';
update it_systems set requester_permission='INHERIT' where requester_permission IS NULL;