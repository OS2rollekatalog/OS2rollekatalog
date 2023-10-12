create index setting_key_idx
    on setting (setting_key);

create index att_type_responsible_ou_idx
    on attestation_attestation (attestation_type, responsible_ou_uuid);
create index att_type_responsible_user_idx
    on attestation_attestation (attestation_type, responsible_user_uuid);

create index aisrae_user_role_id_index
    on attestation_it_system_role_attestation_entry (user_role_id);
create index aisuae_user_uuid_index
    on attestation_it_system_user_attestation_entry (user_uuid);

create index aouae_user_uuid_index
    on attestation_organisation_user_attestation_entry (user_uuid);

create index aora_record_hash_valid_from_index
    on attestation_ou_role_assignments (record_hash, valid_from);
create index aoua_valids__index
    on attestation_ou_role_assignments (valid_from, valid_to);

create index sra_hash_valid_index
    on attestation_system_role_assignments (record_hash, valid_from);

create index aura_responsible_user_valid_it_system_index
    on attestation_user_role_assignments (responsible_user_uuid, valid_from, it_system_id);
create index aura_responsible_ou_valid__index
    on attestation_user_role_assignments (responsible_ou_uuid, valid_from);
create index aura_updated_at__index
    on attestation_user_role_assignments (updated_at);

create index am_email_type__index
    on attestation_mail (email_type);
