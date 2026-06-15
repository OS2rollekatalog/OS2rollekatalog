-- Drop the responsible_user_* columns that V1_352 used to drop inline. They are kept alive until
-- now so V1_359 can read responsible_user_uuid while batch-filling the collection FK columns.
--
-- NOTE (Galera): each DROP COLUMN below runs as Total Order Isolation DDL and rebuilds the table,
-- briefly blocking the whole cluster. This is the only remaining cluster-blocking operation in the
-- multi-owner/multi-responsible change set; run it in a maintenance window (or via RSU) if the
-- tables are large.

ALTER TABLE attestation_attestation
    DROP COLUMN IF EXISTS responsible_user_uuid,
    DROP COLUMN IF EXISTS responsible_user_id,
    DROP COLUMN IF EXISTS responsible_user_name;

ALTER TABLE attestation_user_role_assignments
    DROP COLUMN IF EXISTS responsible_user_uuid;

ALTER TABLE attestation_ou_role_assignments
    DROP COLUMN IF EXISTS responsible_user_uuid;

ALTER TABLE attestation_system_role_assignments
    DROP COLUMN IF EXISTS responsible_user_uuid;
