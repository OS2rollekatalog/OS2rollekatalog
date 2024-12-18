alter table attestation_user_role_assignments
    add extra_sensitive_role bit default 0 not null;
go
alter table attestation_ou_role_assignments
    add extra_sensitive_role bit default 0 not null;
go
