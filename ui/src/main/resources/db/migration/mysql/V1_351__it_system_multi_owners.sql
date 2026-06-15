-- Live-side: many-to-many attestation responsibles
CREATE TABLE it_system_attestation_responsible (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    itsystem_id BIGINT NOT NULL,
    user_uuid   VARCHAR(36) NOT NULL,
    UNIQUE KEY uk_it_system_att_resp (itsystem_id, user_uuid),
    CONSTRAINT fk_it_system_att_resp_system FOREIGN KEY (itsystem_id) REFERENCES it_systems (id) ON DELETE CASCADE,
    CONSTRAINT fk_it_system_att_resp_user   FOREIGN KEY (user_uuid)   REFERENCES users (uuid)    ON DELETE CASCADE
);

INSERT INTO it_system_attestation_responsible (itsystem_id, user_uuid)
SELECT id, attestation_responsible_uuid
FROM it_systems
WHERE attestation_responsible_uuid IS NOT NULL;

-- Live-side: many-to-many system owners
CREATE TABLE it_system_system_owner (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    itsystem_id BIGINT NOT NULL,
    user_uuid   VARCHAR(36) NOT NULL,
    UNIQUE KEY uk_it_system_owner (itsystem_id, user_uuid),
    CONSTRAINT fk_it_system_owner_system FOREIGN KEY (itsystem_id) REFERENCES it_systems (id) ON DELETE CASCADE,
    CONSTRAINT fk_it_system_owner_user   FOREIGN KEY (user_uuid)   REFERENCES users (uuid)    ON DELETE CASCADE
);

INSERT INTO it_system_system_owner (itsystem_id, user_uuid)
SELECT id, system_owner_uuid
FROM it_systems
WHERE system_owner_uuid IS NOT NULL;

ALTER TABLE it_systems DROP FOREIGN KEY fk_it_systems_user;
ALTER TABLE it_systems DROP FOREIGN KEY fk_it_systems_user_system;
ALTER TABLE it_systems DROP COLUMN attestation_responsible_uuid;
ALTER TABLE it_systems DROP COLUMN system_owner_uuid;

-- History-side: normalize the per-day snapshot to many-to-many as well, so reports
-- for any past date can list all responsibles/owners that were active on that date.
CREATE TABLE history_it_system_attestation_responsible (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    history_it_systems_id BIGINT NOT NULL,
    user_uuid             VARCHAR(36) NOT NULL,
    UNIQUE KEY uk_history_it_system_att_resp (history_it_systems_id, user_uuid),
    CONSTRAINT fk_history_it_system_att_resp FOREIGN KEY (history_it_systems_id)
        REFERENCES history_it_systems (id) ON DELETE CASCADE
);

INSERT INTO history_it_system_attestation_responsible (history_it_systems_id, user_uuid)
SELECT id, attestation_responsible_uuid
FROM history_it_systems
WHERE attestation_responsible_uuid IS NOT NULL;

CREATE TABLE history_it_system_system_owner (
    id                    BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    history_it_systems_id BIGINT NOT NULL,
    user_uuid             VARCHAR(36) NOT NULL,
    UNIQUE KEY uk_history_it_system_owner (history_it_systems_id, user_uuid),
    CONSTRAINT fk_history_it_system_owner FOREIGN KEY (history_it_systems_id)
        REFERENCES history_it_systems (id) ON DELETE CASCADE
);

INSERT INTO history_it_system_system_owner (history_it_systems_id, user_uuid)
SELECT id, system_owner_uuid
FROM history_it_systems
WHERE system_owner_uuid IS NOT NULL;

ALTER TABLE history_it_systems DROP COLUMN attestation_responsible_uuid;
ALTER TABLE history_it_systems DROP COLUMN system_owner_uuid;

-- Attestation responsible collection: used by the attestation tracker to group
-- all responsible users for a given IT system into one referenceable unit.
CREATE TABLE attestation_attestation_responsible_collection (
    id           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    -- Nullable: an OU-driven attestation has no IT system. Must match the entity
    -- (AttestationResponsibleCollection.itSystemId is a nullable Long) and V1_352, which
    -- creates this table with NULL too — whichever runs first wins, so they must agree.
    it_system_id BIGINT NULL
);

CREATE TABLE attestation_attestation_responsible_collection_uuids (
    attestation_responsible_collection_id BIGINT      NOT NULL,
    users_uuid                            VARCHAR(36) NOT NULL,
    CONSTRAINT fk_arc_collection FOREIGN KEY (attestation_responsible_collection_id)
        REFERENCES attestation_attestation_responsible_collection (id) ON DELETE CASCADE
);

-- Index it_system_id: looked up by it_system_id at runtime (findFirstByItSystemId), by
-- V1_352's _tmp joins, and by V1_359's batched back-fill. Without it those joins degrade to a
-- nested-loop scan of this table per probe — the same plan that made the old back-fill take
-- ~5 min. Non-unique so the OU-driven (NULL it_system_id) case stays unconstrained.
CREATE INDEX idx_arc_it_system_id
    ON attestation_attestation_responsible_collection (it_system_id);

-- Seed one collection row per IT system that has at least one responsible,
-- and populate the UUIDs from the new it_system_attestation_responsible table.
INSERT INTO attestation_attestation_responsible_collection (it_system_id)
SELECT DISTINCT itsystem_id FROM it_system_attestation_responsible;

INSERT INTO attestation_attestation_responsible_collection_uuids (attestation_responsible_collection_id, users_uuid)
SELECT arc.id, r.user_uuid
FROM it_system_attestation_responsible r
JOIN attestation_attestation_responsible_collection arc ON arc.it_system_id = r.itsystem_id;

-- Add the collection FK column to the three temporal assignment tables.
-- The back-fill of this column is handled (and was already handled identically) by
-- V1_352, which recomputes it via a primary-key join — verified row-for-row to produce
-- the exact same result across 1.388.270 rows. The previous back-fill here joined arc on
-- the unindexed it_system_id and took ~5 min on prod data; it has been removed as redundant.
ALTER TABLE attestation_user_role_assignments  ADD COLUMN attestation_responsible_collection_id BIGINT NULL;
ALTER TABLE attestation_ou_role_assignments    ADD COLUMN attestation_responsible_collection_id BIGINT NULL;
ALTER TABLE attestation_system_role_assignments ADD COLUMN attestation_responsible_collection_id BIGINT NULL;
