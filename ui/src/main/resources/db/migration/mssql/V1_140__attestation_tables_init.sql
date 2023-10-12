
alter table history_it_systems
    add attestation_responsible_uuid nvarchar(36) default null;

alter table history_user_roles_system_roles
    add system_role_id bigint;
alter table history_user_roles_system_roles
    add system_role_description nvarchar(max);

alter table history_role_assignments
    add ou_uuid nvarchar(36) default null;
alter table history_role_assignments 
    add role_role_group_id bigint null;

alter table history_user_roles
    add sensitive_role bit;
alter table history_user_roles
    add role_assignment_attestation_by_attestation_responsible bit default 0;

alter table history_ou_role_assignments
    add role_role_group_id bigint null;

alter table history_role_assignment_titles
    add role_role_group_id bigint null;

alter table history_role_assignment_excepted_users
    add role_role_group_id bigint null;

create table attestation_attestation
(
    id                    bigint not null primary key identity (1, 1),
    uuid                  nvarchar(36)  not null index IX_attestation_attestation_uuid,
    created_at            date         not null,
    deadline              date         null,
    verified_at           datetimeoffset  null,
    attestation_type      nvarchar(60)  not null,
    it_system_id          bigint       null,
    it_system_name        nvarchar(255) null,
    responsible_user_id   nvarchar(255) null,
    responsible_user_uuid nvarchar(36)  null,
    responsible_user_name nvarchar(255) null,
    responsible_ou_name   nvarchar(255) null,
    responsible_ou_uuid   nvarchar(36)  null
    );

create table attestation_user
(
    id                    bigint not null primary key identity (1, 1),
    user_uuid             nvarchar(36)  null,
    attestation_id        bigint       not null index IX_attestation_user_attestation_id,
    sensitive_roles       bit          not null,
    constraint fk_asu_attestation_id
        foreign key (attestation_id) references attestation_attestation (id)
);

create table attestation_organisation_user_attestation_entry
(
    id                     bigint not null primary key identity (1, 1),
    remarks                nvarchar(255) null,
    ad_removal             bit not null,
    user_uuid              nvarchar(36) null,
    performed_by_user_id   nvarchar(255) null,
    performed_by_user_uuid nvarchar(36) not null,
    attestation_id         bigint not null index IX_apiae_attestation_id,
    created_at             datetimeoffset not null,
    constraint fk_aoua_attestation_id
        foreign key (attestation_id) references attestation_attestation (id)
);


create table attestation_it_system_user_attestation_entry
(
    id                            bigint not null primary key identity (1, 1),
    remarks                       nvarchar(255) null,
    user_uuid                     nvarchar(36) null,
    performed_by_user_id          nvarchar(255) null,
    performed_by_user_uuid        nvarchar(36) not null,
    attestation_id                bigint not null index IX_aisuae_attestation_id,
    created_at                    datetimeoffset not null,
    constraint fk_ausa_attestation_id
        foreign key (attestation_id)
            references attestation_attestation (id)
);

create table attestation_it_system_role_attestation_entry
(
    id                            bigint not null primary key identity (1, 1),
    remarks                       nvarchar(255) null,
    user_role_id                  nvarchar(255) null,
    performed_by_user_id          nvarchar(255) null,
    performed_by_user_uuid        nvarchar(36) not null,
    attestation_id                bigint not null index IX_aisrae_attestation_id,
    created_at                     datetimeoffset not null,
    constraint fk_isura_attestation_id
        foreign key (attestation_id)
            references attestation_attestation (id)
);

create table attestation_organisation_role_attestation_entry
(
    attestation_id                bigint primary key,
    remarks                       nvarchar(255) null,
    performed_by_user_id          nvarchar(255) null,
    performed_by_user_uuid        nvarchar(36) not null,
    created_at                    datetimeoffset not null,
    constraint fk_ora_attestation_id
        foreign key (attestation_id)
            references attestation_attestation (id)
);

create table attestation_ou_role_assignments
(
    id                     bigint not null primary key identity (1, 1),
    record_hash            nvarchar(255) not null,
    updated_at             date         null,
    valid_from             date         not null,
    valid_to               date         null,
    assigned_through_name  nvarchar(255) null,
    assigned_through_type  nvarchar(255) null,
    assigned_through_uuid  nvarchar(255) null,
    inherited              bit           null,
    it_system_id           bigint        null,
    it_system_name         nvarchar(255) null,
    role_description       nvarchar(max) null,
    role_id                bigint        null,
    role_name              nvarchar(255) null,
    role_group_name        nvarchar(255) null,
    role_group_id          bigint        null,
    role_group_description nvarchar(max) null,
    sensitive_role         bit           null,
    ou_name                nvarchar(255) null,
    ou_uuid                nvarchar(255) not null,
    excepted_user_uuids    nvarchar(max) null,
    title_uuids            nvarchar(max) null
);

create table attestation_system_role_assignments
(
    id                      bigint not null primary key identity (1, 1),
    record_hash             nvarchar(255) not null,
    updated_at              date          null,
    valid_from              date          not null,
    valid_to                date          null,
    it_system_id            bigint        null,
    it_system_name          nvarchar(255) null,
    responsible_user_uuid   nvarchar(36)  null,
    system_role_description nvarchar(max) null,
    system_role_id          bigint        null,
    system_role_name        nvarchar(255) null,
    user_role_description   nvarchar(max) null,
    user_role_id            bigint        null,
    user_role_name          nvarchar(255) null
);

create table attestation_user_role_assignments
(
    id                     bigint not null primary key identity (1, 1),
    record_hash            nvarchar(255) not null,
    updated_at             date          null,
    valid_from             date          not null,
    valid_to               date          null,
    assigned_through_name  nvarchar(255) null,
    assigned_through_type  nvarchar(255) null,
    assigned_through_uuid  nvarchar(36)  null,
    inherited              bit           null,
    responsible_ou_name    nvarchar(255) null,
    responsible_ou_uuid    nvarchar(36)  null,
    manager                bit           not null,
    responsible_user_uuid  nvarchar(36)  null,
    sensitive_role         bit           null,
    user_role_description  nvarchar(max) null,
    role_group_id          bigint        null,
    role_group_name        nvarchar(255) null,
    role_group_description nvarchar(max) null,
    user_role_id           bigint        null,
    user_role_name         nvarchar(255) null,
    user_uuid              nvarchar(36)  null,
    user_id				   nvarchar(64)  null,
    user_name              nvarchar(128) null,
    it_system_id           bigint        null,
    it_system_name         nvarchar(255) null,
    role_ou_uuid           nvarchar(36)  null,
    role_ou_name           nvarchar(255) null
);

create table attestation_system_role_assignment_constraints
(
    id         bigint not null primary key identity (1, 1),
    attestation_system_role_assignments_id
               bigint      not null index IX_asrac_assignment_id,
    name       nvarchar(64) null,
    value_Type nvarchar(64) null,
    value      nvarchar(max) null,
    constraint fk_attestation_system_role_assignment_id
        foreign key (attestation_system_role_assignments_id)
            references attestation_system_role_assignments (id)
);

create table attestation_mail
(
    id                  bigint not null primary key identity (1, 1),
    email_type          nvarchar(100)  not null,
    email_template_type nvarchar(100)  not null,
    sent_at             datetimeoffset   not null,
    attestation_id      bigint       not null index IX_attestation_mail_attestation_id,
    constraint fk_am_attestation_id
        foreign key (attestation_id) references attestation_attestation (id)
);
