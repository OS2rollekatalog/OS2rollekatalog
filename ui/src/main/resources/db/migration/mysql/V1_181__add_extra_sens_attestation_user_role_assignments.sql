alter table attestation_user_role_assignments
    add extra_sensitive_role BIT default 0 not null after sensitive_role;
alter table attestation_ou_role_assignments
    add extra_sensitive_role BIT default 0 not null after sensitive_role;
