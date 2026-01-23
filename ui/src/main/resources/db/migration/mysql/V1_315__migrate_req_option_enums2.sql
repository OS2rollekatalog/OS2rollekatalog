
alter table rolegroup
    modify requester_permission varchar(1024) default 'INHERIT' not null;
alter table rolegroup
    modify approver_permission varchar(1024) default 'INHERIT' not null;

update rolegroup set approver_permission='AUTHORIZED,MANAGERORSUBSTITUTE,AUTHRESPONSIBLE,ADMINISTRATOR' where approver_permission='AUTHORIZEDMANAGERORAUTHRESPONSIBLE';
update rolegroup set approver_permission='MANAGERORSUBSTITUTE,AUTHRESPONSIBLE,ADMINISTRATOR' where approver_permission='MANAGERORAUTHRESPONSIBLE';
update rolegroup set approver_permission='AUTHORIZED,ADMINISTRATOR' where approver_permission='AUTHORIZEDONLY';
update rolegroup set approver_permission='ADMINISTRATOR' where approver_permission='ADMINONLY';
update rolegroup set approver_permission='SYSTEMRESPONSIBLE,ADMINISTRATOR' where approver_permission='SYSTEMRESPONSIBLE';

update rolegroup set requester_permission='EMPLOYEE,ADMIN' where requester_permission='EMPLOYEESONLY';
update rolegroup set requester_permission='EMPLOYEE,AUTHRESPONSIBLE,AUTHORIZED,MANAGERORSUBSTITUTE,ADMIN' where requester_permission='ALL';
update rolegroup set requester_permission='EMPLOYEE,AUTHRESPONSIBLE,MANAGERORSUBSTITUTE,ADMIN' where requester_permission='EMPLOYEESMANAGERSANDAUTHRESPONSIBLE';
update rolegroup set requester_permission='AUTHORIZED,AUTHRESPONSIBLE,MANAGERORSUBSTITUTE,ADMIN' where requester_permission='AUTHORIZEDMANAGERSANDAUTHRESPONSIBLE';
update rolegroup set requester_permission='AUTHORIZED,ADMIN' where requester_permission='AUTHORIZEDONLY';
update rolegroup set requester_permission='MANAGERORSUBSTITUTE,AUTHRESPONSIBLE' where requester_permission='MANAGERANDAUTHRESPONSIBLE';
