create index aura_user_uuid_valid
    on attestation_user_role_assignments (user_uuid, valid_from);
