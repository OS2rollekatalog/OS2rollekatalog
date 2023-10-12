
alter table history_it_systems
    add column attestation_responsible_uuid varchar(36) default null;
alter table history_user_roles_system_roles
    add column system_role_id bigint,
    add column system_role_description text;
alter table history_role_assignments
    add column ou_uuid varchar(36) default null,
    add column role_role_group_id bigint null;
alter table history_user_roles
    add column sensitive_role tinyint(1),
    add column role_assignment_attestation_by_attestation_responsible tinyint(1) default false;
alter table history_ou_role_assignments
    add column role_role_group_id bigint null;
alter table history_role_assignment_titles
    add column role_role_group_id bigint null;
alter table history_role_assignment_excepted_users
    add column role_role_group_id bigint null;

create table if not exists attestation_attestation
(
    id                    bigint auto_increment primary key,
    uuid                  varchar(36)  not null,
    created_at            date         not null,
    deadline              date         null,
    verified_at           datetime(6)  null,
    attestation_type      varchar(60)  not null,
    it_system_id          bigint       null,
    it_system_name        varchar(255) null,
    responsible_user_id   varchar(255) null,
    responsible_user_uuid varchar(36)  null,
    responsible_user_name varchar(255) null,
    responsible_ou_name   varchar(255) null,
    responsible_ou_uuid   varchar(36)  null,
index (uuid)
);

create table if not exists attestation_user
(
    id                    bigint auto_increment primary key,
    user_uuid             varchar(36)  null,
    attestation_id        bigint       not null,
    sensitive_roles       bit          not null,
    constraint fk_asu_attestation_id
        foreign key (attestation_id) references attestation_attestation (id)
);

create table if not exists attestation_organisation_user_attestation_entry
(
    id                     bigint auto_increment primary key,
    remarks                varchar(255) null,
    ad_removal             bit not null,
    user_uuid              varchar(36) null,
    performed_by_user_id   varchar(255) null,
    performed_by_user_uuid varchar(36) not null,
    attestation_id         bigint not null,
    created_at             datetime(6) not null,
    constraint fk_aoua_attestation_id
        foreign key (attestation_id) references attestation_attestation (id)
);


create table if not exists attestation_it_system_user_attestation_entry
(
    id                            bigint auto_increment primary key,
    remarks                       varchar(255) null,
    user_uuid                     varchar(36) null,
    performed_by_user_id          varchar(255) null,
    performed_by_user_uuid        varchar(36) not null,
    attestation_id                bigint not null,
    created_at                    datetime(6) not null,
    constraint fk_ausa_attestation_id
        foreign key (attestation_id)
            references attestation_attestation (id)
);

create table if not exists attestation_it_system_role_attestation_entry
(
    id                            bigint auto_increment primary key,
    remarks                       varchar(255) null,
    user_role_id                  varchar(255) null,
    performed_by_user_id          varchar(255) null,
    performed_by_user_uuid        varchar(36) not null,
    attestation_id                bigint not null,
    created_at                    datetime(6) not null,
    constraint fk_isura_attestation_id
        foreign key (attestation_id)
            references attestation_attestation (id)
);

create table if not exists attestation_organisation_role_attestation_entry
(
    attestation_id                bigint primary key,
    remarks                       varchar(255) null,
    performed_by_user_id          varchar(255) null,
    performed_by_user_uuid        varchar(36) not null,
    created_at                    datetime(6) not null,
    constraint fk_ora_attestation_id
        foreign key (attestation_id)
            references attestation_attestation (id)
);

create table if not exists attestation_ou_role_assignments
(
    id                     bigint auto_increment primary key,
    record_hash            varchar(255) not null,
    updated_at             date         null,
    valid_from             date         not null,
    valid_to               date         null,
    assigned_through_name  varchar(255) null,
    assigned_through_type  varchar(255) null,
    assigned_through_uuid  varchar(255) null,
    inherited              bit          null,
    it_system_id           bigint       null,
    it_system_name         varchar(255) null,
    role_description       text         null,
    role_id                bigint       null,
    role_name              varchar(255) null,
    role_group_name        varchar(255) null,
    role_group_id          bigint       null,
    role_group_description text         null,
    sensitive_role         bit          null,
    ou_name                varchar(255) null,
    ou_uuid                varchar(255) not null,
    excepted_user_uuids    text         null,
    title_uuids            text         null
);

create table if not exists attestation_system_role_assignments
(
    id                      bigint auto_increment primary key,
    record_hash             varchar(255) not null,
    updated_at              date         null,
    valid_from              date         not null,
    valid_to                date         null,
    it_system_id            bigint       null,
    it_system_name          varchar(255) null,
    responsible_user_uuid   varchar(36)  null,
    system_role_description text         null,
    system_role_id          bigint       null,
    system_role_name        varchar(255) null,
    user_role_description   text         null,
    user_role_id            bigint       null,
    user_role_name          varchar(255) null
);

create table if not exists attestation_user_role_assignments
(
    id                     bigint auto_increment primary key,
    record_hash            varchar(255) not null,
    updated_at             date         null,
    valid_from             date         not null,
    valid_to               date         null,
    assigned_through_name  varchar(255) null,
    assigned_through_type  varchar(255) null,
    assigned_through_uuid  varchar(36)  null,
    inherited              bit          null,
    responsible_ou_name    varchar(255) null,
    responsible_ou_uuid    varchar(36)  null,
    manager                bit          not null,
    responsible_user_uuid  varchar(36)  null,
    sensitive_role         bit          null,
    user_role_description  text         null,
    role_group_id          bigint       null,
    role_group_name        varchar(255) null,
    role_group_description text         null,
    user_role_id           bigint       null,
    user_role_name         varchar(255) null,
    user_uuid              varchar(36)  null,
    user_id				   varchar(64)  null,
    user_name              varchar(128) null,
    it_system_id           bigint       null,
    it_system_name         varchar(255) null,
    role_ou_uuid           varchar(36)  null,
    role_ou_name           varchar(255) null
);

create table if not exists attestation_system_role_assignment_constraints
(
    id         bigint auto_increment primary key,
    attestation_system_role_assignments_id
               bigint      not null,
    name       varchar(64) null,
    value_Type varchar(64) null,
    value      text null,
    constraint fk_attestation_system_role_assignment_id
        foreign key (attestation_system_role_assignments_id)
            references attestation_system_role_assignments (id)
);

create table if not exists attestation_mail
(
    id                  bigint auto_increment primary key,
    email_type          varchar(100)  not null,
    email_template_type varchar(100)  not null,
    sent_at             datetime(6)   not null,
    attestation_id      bigint       not null,
    constraint fk_am_attestation_id
        foreign key (attestation_id) references attestation_attestation (id)
);

create table if not exists attestation_locks
(
    lock_id     varchar(255) not null,
    version     bigint       null,
    acquired_at datetime     null,
    constraint pk_attestation_locks primary key (lock_id)
);
