CREATE INDEX idx_ura_responsible_ou_valid_from
    ON attestation_user_role_assignments (responsible_ou_uuid, valid_from DESC);
