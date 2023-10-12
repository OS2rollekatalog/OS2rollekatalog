create index aura_valids
    on attestation_user_role_assignments (valid_from, valid_to);
create index aura_hash_valid_from
    on attestation_user_role_assignments (record_hash, valid_from, valid_to);


drop index aouae_user_uuid_index on attestation_organisation_user_attestation_entry;
create index aouae_user_uuid_index
    on attestation_organisation_user_attestation_entry (user_uuid, created_at);

create index aorae_created_at
    on attestation_organisation_role_attestation_entry (attestation_id, created_at);


drop index att_type_responsible_ou_idx on attestation_attestation;
create index att_type_responsible_ou_idx
    on attestation_attestation (attestation_type, responsible_ou_uuid, deadline);
