-- -----------------------------------------------------------------------
-- historic_assignment: replace responsible_user_uuid with responsible_collection_id
-- -----------------------------------------------------------------------
ALTER TABLE historic_assignment
    ADD COLUMN IF NOT EXISTS responsible_collection_id BIGINT NULL;

ALTER TABLE historic_assignment
    DROP COLUMN IF EXISTS responsible_user_uuid;

-- -----------------------------------------------------------------------
-- Create the collection table: one row per IT-system (or null for OU-driven attestations)
CREATE TABLE IF NOT EXISTS attestation_attestation_responsible_collection
(
    id           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    it_system_id BIGINT NULL
);

-- One UUID per collection entry
CREATE TABLE IF NOT EXISTS attestation_attestation_responsible_collection_uuids
(
    attestation_responsible_collection_id BIGINT      NOT NULL,
    users_uuid                            VARCHAR(36) NOT NULL,
    CONSTRAINT fk_arc_uuids_collection
        FOREIGN KEY (attestation_responsible_collection_id)
            REFERENCES attestation_attestation_responsible_collection (id) ON DELETE CASCADE
);

-- -----------------------------------------------------------------------
-- attestation_attestation
-- -----------------------------------------------------------------------
INSERT INTO attestation_attestation_responsible_collection (it_system_id)
SELECT DISTINCT it_system_id
FROM attestation_attestation
WHERE responsible_user_uuid IS NOT NULL
  AND it_system_id NOT IN (SELECT it_system_id
                           FROM attestation_attestation_responsible_collection
                           WHERE it_system_id IS NOT NULL);

CREATE TEMPORARY TABLE _tmp_att_collection AS
SELECT aa.id                                   AS attestation_id,
       arc.id                                  AS collection_id,
       aa.responsible_user_uuid,
       aa.it_system_id
FROM attestation_attestation aa
         JOIN attestation_attestation_responsible_collection arc
              ON arc.it_system_id <=> aa.it_system_id
WHERE aa.responsible_user_uuid IS NOT NULL;

INSERT INTO attestation_attestation_responsible_collection_uuids
    (attestation_responsible_collection_id, users_uuid)
SELECT DISTINCT collection_id, responsible_user_uuid
FROM _tmp_att_collection;

ALTER TABLE attestation_attestation
    ADD COLUMN IF NOT EXISTS responsible_collection_id BIGINT NULL;

-- Back-fill of responsible_collection_id is done by V1_359 (batched, Galera-safe).
-- responsible_user_uuid/_id/_name are dropped in V1_360, after that back-fill runs.
DROP TEMPORARY TABLE _tmp_att_collection;

-- -----------------------------------------------------------------------
-- attestation_user_role_assignments
-- -----------------------------------------------------------------------
INSERT INTO attestation_attestation_responsible_collection (it_system_id)
SELECT DISTINCT it_system_id
FROM attestation_user_role_assignments
WHERE responsible_user_uuid IS NOT NULL
  AND it_system_id NOT IN (SELECT it_system_id
                           FROM attestation_attestation_responsible_collection
                           WHERE it_system_id IS NOT NULL);

CREATE TEMPORARY TABLE _tmp_ura_collection AS
SELECT ura.id                                   AS row_id,
       arc.id                                   AS collection_id,
       ura.responsible_user_uuid,
       ura.it_system_id
FROM attestation_user_role_assignments ura
         JOIN attestation_attestation_responsible_collection arc
              ON arc.it_system_id <=> ura.it_system_id
WHERE ura.responsible_user_uuid IS NOT NULL;

INSERT INTO attestation_attestation_responsible_collection_uuids
    (attestation_responsible_collection_id, users_uuid)
SELECT DISTINCT collection_id, responsible_user_uuid
FROM _tmp_ura_collection
WHERE (collection_id, responsible_user_uuid) NOT IN
      (SELECT attestation_responsible_collection_id, users_uuid
       FROM attestation_attestation_responsible_collection_uuids);

ALTER TABLE attestation_user_role_assignments
    ADD COLUMN IF NOT EXISTS attestation_responsible_collection_id BIGINT NULL;

-- Back-fill done by V1_359 (batched); responsible_user_uuid dropped in V1_360.
DROP TEMPORARY TABLE _tmp_ura_collection;

-- -----------------------------------------------------------------------
-- attestation_ou_role_assignments
-- -----------------------------------------------------------------------
INSERT INTO attestation_attestation_responsible_collection (it_system_id)
SELECT DISTINCT it_system_id
FROM attestation_ou_role_assignments
WHERE responsible_user_uuid IS NOT NULL
  AND it_system_id NOT IN (SELECT it_system_id
                           FROM attestation_attestation_responsible_collection
                           WHERE it_system_id IS NOT NULL);

CREATE TEMPORARY TABLE _tmp_ora_collection AS
SELECT ora.id                                   AS row_id,
       arc.id                                   AS collection_id,
       ora.responsible_user_uuid,
       ora.it_system_id
FROM attestation_ou_role_assignments ora
         JOIN attestation_attestation_responsible_collection arc
              ON arc.it_system_id <=> ora.it_system_id
WHERE ora.responsible_user_uuid IS NOT NULL;

INSERT INTO attestation_attestation_responsible_collection_uuids
    (attestation_responsible_collection_id, users_uuid)
SELECT DISTINCT collection_id, responsible_user_uuid
FROM _tmp_ora_collection
WHERE (collection_id, responsible_user_uuid) NOT IN
      (SELECT attestation_responsible_collection_id, users_uuid
       FROM attestation_attestation_responsible_collection_uuids);

ALTER TABLE attestation_ou_role_assignments
    ADD COLUMN IF NOT EXISTS attestation_responsible_collection_id BIGINT NULL;

-- Back-fill done by V1_359 (batched); responsible_user_uuid dropped in V1_360.
DROP TEMPORARY TABLE _tmp_ora_collection;

-- -----------------------------------------------------------------------
-- attestation_system_role_assignments
-- -----------------------------------------------------------------------
INSERT INTO attestation_attestation_responsible_collection (it_system_id)
SELECT DISTINCT it_system_id
FROM attestation_system_role_assignments
WHERE responsible_user_uuid IS NOT NULL
  AND it_system_id NOT IN (SELECT it_system_id
                           FROM attestation_attestation_responsible_collection
                           WHERE it_system_id IS NOT NULL);

CREATE TEMPORARY TABLE _tmp_sra_collection AS
SELECT sra.id                                   AS row_id,
       arc.id                                   AS collection_id,
       sra.responsible_user_uuid,
       sra.it_system_id
FROM attestation_system_role_assignments sra
         JOIN attestation_attestation_responsible_collection arc
              ON arc.it_system_id <=> sra.it_system_id
WHERE sra.responsible_user_uuid IS NOT NULL;

INSERT INTO attestation_attestation_responsible_collection_uuids
    (attestation_responsible_collection_id, users_uuid)
SELECT DISTINCT collection_id, responsible_user_uuid
FROM _tmp_sra_collection
WHERE (collection_id, responsible_user_uuid) NOT IN
      (SELECT attestation_responsible_collection_id, users_uuid
       FROM attestation_attestation_responsible_collection_uuids);

ALTER TABLE attestation_system_role_assignments
    ADD COLUMN IF NOT EXISTS attestation_responsible_collection_id BIGINT NULL;

-- Back-fill done by V1_359 (batched); responsible_user_uuid dropped in V1_360.
DROP TEMPORARY TABLE _tmp_sra_collection;
