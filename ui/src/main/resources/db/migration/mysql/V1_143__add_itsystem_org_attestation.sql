

create table if not exists attestation_it_system_organisation_attestation_entry
(
    id                     bigint auto_increment primary key,
    remarks                text null,
    organisation_uuid      varchar(36) null,
    performed_by_user_id   varchar(255) null,
    performed_by_user_uuid varchar(36) not null,
    attestation_id         bigint not null,
    created_at             datetime(6) not null,
    constraint fk_aisoa_attestation_id
        foreign key (attestation_id) references attestation_attestation (id)
);

alter table attestation_ou_role_assignments 
    add column responsible_user_uuid varchar(36)  null,
    add column responsible_ou_name   varchar(255) null,
    add column responsible_ou_uuid   varchar(36) null,
    add column inherit bit null;

alter table attestation_organisation_user_attestation_entry
    modify column remarks text null;
alter table attestation_it_system_role_attestation_entry
    modify column remarks text null;
alter table attestation_organisation_role_attestation_entry
    modify column remarks text null;
alter table attestation_it_system_user_attestation_entry
    modify column remarks text null;

alter table history_ou_role_assignments
    add column inherit bit null;
