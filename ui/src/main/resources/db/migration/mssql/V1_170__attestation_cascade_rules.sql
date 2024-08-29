alter table attestation_user
    drop constraint fk_asu_attestation_id
    go

alter table attestation_user
    add constraint fk_asu_attestation_id
        foreign key (attestation_id) references attestation_attestation
            on delete cascade
    go


alter table attestation_organisation_user_attestation_entry
    drop constraint fk_aoua_attestation_id
    go

alter table attestation_organisation_user_attestation_entry
    add constraint fk_aoua_attestation_id
        foreign key (attestation_id) references attestation_attestation
            on delete cascade
    go



alter table attestation_organisation_role_attestation_entry
    drop constraint fk_ora_attestation_id
    go

alter table attestation_organisation_role_attestation_entry
    add constraint fk_ora_attestation_id
        foreign key (attestation_id) references attestation_attestation
            on delete cascade
    go



alter table attestation_it_system_user_attestation_entry
    drop constraint fk_ausa_attestation_id
    go

alter table attestation_it_system_user_attestation_entry
    add constraint fk_ausa_attestation_id
        foreign key (attestation_id) references attestation_attestation
            on delete cascade
    go


alter table attestation_it_system_role_attestation_entry
    drop constraint fk_isura_attestation_id
    go

alter table attestation_it_system_role_attestation_entry
    add constraint fk_isura_attestation_id
        foreign key (attestation_id) references attestation_attestation
            on delete cascade
    go

alter table attestation_it_system_organisation_attestation_entry
    drop constraint fk_aisoa_attestation_id
    go

alter table attestation_it_system_organisation_attestation_entry
    add constraint fk_aisoa_attestation_id
        foreign key (attestation_id) references attestation_attestation
            on delete cascade
    go

