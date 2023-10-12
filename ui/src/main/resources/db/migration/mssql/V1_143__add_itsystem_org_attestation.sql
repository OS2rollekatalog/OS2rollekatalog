

create table attestation_it_system_organisation_attestation_entry
(
    id                     bigint not null primary key identity (1, 1),
    remarks                nvarchar(MAX) null,
    organisation_uuid      nvarchar(36) null,
    performed_by_user_id   nvarchar(255) null,
    performed_by_user_uuid nvarchar(36) not null,
    attestation_id         bigint not null,
    created_at             date not null,
    constraint fk_aisoa_attestation_id
        foreign key (attestation_id) references attestation_attestation (id)
);

alter table attestation_ou_role_assignments
    add responsible_user_uuid nvarchar(36)  null;
alter table attestation_ou_role_assignments
    add responsible_ou_name   nvarchar(255) null;
alter table attestation_ou_role_assignments
    add responsible_ou_uuid   nvarchar(36) null;
alter table attestation_ou_role_assignments
    add inherit bit null;

alter table attestation_organisation_user_attestation_entry
    alter column remarks nvarchar(MAX) null;
alter table attestation_it_system_role_attestation_entry
    alter column remarks nvarchar(MAX) null;
alter table attestation_organisation_role_attestation_entry
    alter column remarks nvarchar(MAX) null;
alter table attestation_it_system_user_attestation_entry
    alter column remarks nvarchar(MAX) null;

alter table history_ou_role_assignments
    add inherit bit null;