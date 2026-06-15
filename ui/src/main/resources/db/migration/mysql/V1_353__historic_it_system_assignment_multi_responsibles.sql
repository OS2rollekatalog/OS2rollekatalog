-- historic_it_system_assignment: replace responsible_user_uuid with responsible_collection_id
ALTER TABLE historic_it_system_assignment
    ADD COLUMN IF NOT EXISTS responsible_collection_id BIGINT NULL;

ALTER TABLE historic_it_system_assignment
    DROP COLUMN IF EXISTS responsible_user_uuid;

-- historic_ou_assignment: replace responsible_user_uuid with responsible_collection_id
ALTER TABLE historic_ou_assignment
    ADD COLUMN IF NOT EXISTS responsible_collection_id BIGINT NULL;

ALTER TABLE historic_ou_assignment
    DROP COLUMN IF EXISTS responsible_user_uuid;
