-- =====================================================================
-- Rebuild history_ou_role_assignments with deduplication and exclusions
-- Optimized for performance
-- =====================================================================

-- Gem nuværende settings og slå checks fra for hurtigere bulk insert
SET @old_fk_checks := @@FOREIGN_KEY_CHECKS;
SET @old_unique_checks := @@UNIQUE_CHECKS;
SET @old_autocommit := @@AUTOCOMMIT;
SET FOREIGN_KEY_CHECKS=0;
SET UNIQUE_CHECKS=0;
SET AUTOCOMMIT=0;

-- 1) Omdøb originalen
RENAME TABLE history_ou_role_assignments TO history_ou_role_assignments_old;

-- 2) Staging-tabel uden AUTO_INCREMENT/PK og med nullable kolonner
CREATE TABLE stage_ou_role_assignments LIKE history_ou_role_assignments_old;

ALTER TABLE stage_ou_role_assignments
    -- fjern AUTO_INCREMENT/PK
    MODIFY id BIGINT NULL,
    DROP PRIMARY KEY,
    -- gør disse nullable (vi indsætter NULL i første SELECT)
    MODIFY assigned_through_type VARCHAR(64) NULL,
    MODIFY assigned_through_uuid VARCHAR(36) NULL,
    MODIFY assigned_through_name VARCHAR(512) NULL,
    -- hvis din old-tabel har inherit NOT NULL, gør den også nullable
    MODIFY `inherit` BIT(1) NULL;

-- 3) Fyld staging med UNION ALL (ingen DISTINCT)
INSERT INTO stage_ou_role_assignments (
    id, dato, ou_uuid, role_id, role_name, role_it_system_id, role_it_system_name,
    role_role_group, assigned_through_type, assigned_through_uuid, assigned_through_name,
    role_role_group_id, inherit, assigned_by_user_id, assigned_by_name,
    assigned_when, start_date, stop_date
)
SELECT NULL, dato, ou_uuid, role_id, role_name, role_it_system_id, role_it_system_name,
       role_role_group, NULL, NULL, NULL,
       role_role_group_id, NULL, assigned_by_user_id, assigned_by_name,
       assigned_when, start_date, stop_date
FROM history_role_assignment_excepted_users
UNION ALL
SELECT NULL, dato, ou_uuid, role_id, role_name, role_it_system_id, role_it_system_name,
       role_role_group, assigned_through_type, assigned_through_uuid, assigned_through_name,
       role_role_group_id, inherit, assigned_by_user_id, assigned_by_name,
       assigned_when, start_date, stop_date
FROM history_role_assignment_negative_titles
UNION ALL
SELECT NULL, dato, ou_uuid, role_id, role_name, role_it_system_id, role_it_system_name,
       role_role_group, assigned_through_type, assigned_through_uuid, assigned_through_name,
       role_role_group_id, inherit, assigned_by_user_id, assigned_by_name,
       assigned_when, start_date, stop_date
FROM history_role_assignment_titles
UNION ALL
SELECT NULL, dato, ou_uuid, role_id, role_name, role_it_system_id, role_it_system_name,
       role_role_group, assigned_through_type, assigned_through_uuid, assigned_through_name,
       role_role_group_id, inherit, assigned_by_user_id, assigned_by_name,
       assigned_when, start_date, stop_date
FROM history_ou_role_assignments_old;

-- 4) Opret ny mål-tabel med dedupe via hash
DROP TABLE IF EXISTS history_ou_role_assignments;
CREATE TABLE history_ou_role_assignments (
                                             id BIGINT NOT NULL AUTO_INCREMENT,
                                             dato DATE NOT NULL,
                                             ou_uuid VARCHAR(36) NOT NULL,
                                             role_id BIGINT NOT NULL,
                                             role_name VARCHAR(128) NOT NULL,
                                             role_it_system_id BIGINT NOT NULL,
                                             role_it_system_name VARCHAR(64) NOT NULL,
                                             role_role_group VARCHAR(128) DEFAULT NULL,
                                             assigned_through_type VARCHAR(64) DEFAULT NULL,
                                             assigned_through_uuid VARCHAR(36) DEFAULT NULL,
                                             assigned_through_name VARCHAR(512) DEFAULT NULL,
                                             role_role_group_id BIGINT DEFAULT NULL,
                                             inherit TINYINT(1) DEFAULT NULL,
                                             assigned_by_user_id VARCHAR(255) NOT NULL,
                                             assigned_by_name VARCHAR(255) NOT NULL,
                                             assigned_when DATETIME NOT NULL,
                                             start_date DATE DEFAULT NULL,
                                             stop_date DATE DEFAULT NULL,
                                             dedupe_hash BINARY(16) AS (
                                                 UNHEX(MD5(CONCAT_WS('#',
                                                                     dato, ou_uuid, role_id, role_name, role_it_system_id, role_it_system_name,
                                                                     role_role_group, assigned_through_type, assigned_through_uuid, assigned_through_name,
                                                                     role_role_group_id, IFNULL(inherit,''), assigned_by_user_id, assigned_by_name,
                                                                     assigned_when, start_date, stop_date)))
                                                 ) STORED,
                                             PRIMARY KEY (id),
                                             UNIQUE KEY uq_dedupe_hash (dedupe_hash)
) ENGINE=InnoDB;

-- 5) Dedupér ved insert
INSERT IGNORE INTO history_ou_role_assignments (
    dato, ou_uuid, role_id, role_name, role_it_system_id, role_it_system_name,
    role_role_group, assigned_through_type, assigned_through_uuid, assigned_through_name,
    role_role_group_id, inherit, assigned_by_user_id, assigned_by_name,
    assigned_when, start_date, stop_date
)
SELECT
    dato, ou_uuid, role_id, role_name, role_it_system_id, role_it_system_name,
    role_role_group, assigned_through_type, assigned_through_uuid, assigned_through_name,
    role_role_group_id, inherit, assigned_by_user_id, assigned_by_name,
    assigned_when, start_date, stop_date
FROM stage_ou_role_assignments;

-- 6) Indeks til join-operationerne
CREATE INDEX ix_assign_match
    ON history_ou_role_assignments (dato, ou_uuid, role_id, assigned_when, assigned_by_user_id);

CREATE INDEX ix_exc_users_match
    ON history_role_assignment_excepted_users (dato, ou_uuid, role_id, assigned_when, assigned_by_user_id);

CREATE INDEX ix_neg_titles_match
    ON history_role_assignment_negative_titles (dato, ou_uuid, role_id, assigned_when, assigned_by_user_id);

CREATE INDEX ix_titles_match
    ON history_role_assignment_titles (dato, ou_uuid, role_id, assigned_when, assigned_by_user_id);

-- 7) Opret exclusions (uden FK i første omgang)
DROP TABLE IF EXISTS history_ou_role_assignment_exclusions;
CREATE TABLE history_ou_role_assignment_exclusions (
                                                       id BIGINT NOT NULL AUTO_INCREMENT,
                                                       assignment_id BIGINT NOT NULL,
                                                       exclusion_type ENUM('excepted_users', 'titles', 'negative_titles') NOT NULL,
                                                       user_uuids TEXT DEFAULT NULL,
                                                       title_uuids TEXT DEFAULT NULL,
                                                       PRIMARY KEY (id)
) ENGINE=InnoDB;

-- 8) Fyld exclusions
INSERT INTO history_ou_role_assignment_exclusions (assignment_id, exclusion_type, user_uuids)
SELECT n.id, 'excepted_users', e.user_uuids
FROM history_role_assignment_excepted_users e
         JOIN history_ou_role_assignments n
              ON n.dato=e.dato AND n.ou_uuid=e.ou_uuid AND n.role_id=e.role_id
                  AND n.assigned_when=e.assigned_when AND n.assigned_by_user_id=e.assigned_by_user_id;

INSERT INTO history_ou_role_assignment_exclusions (assignment_id, exclusion_type, title_uuids)
SELECT n.id, 'negative_titles', t.title_uuids
FROM history_role_assignment_negative_titles t
         JOIN history_ou_role_assignments n
              ON n.dato=t.dato AND n.ou_uuid=t.ou_uuid AND n.role_id=t.role_id
                  AND n.assigned_when=t.assigned_when AND n.assigned_by_user_id=t.assigned_by_user_id;

INSERT INTO history_ou_role_assignment_exclusions (assignment_id, exclusion_type, title_uuids)
SELECT n.id, 'titles', t.title_uuids
FROM history_role_assignment_titles t
         JOIN history_ou_role_assignments n
              ON n.dato=t.dato AND n.ou_uuid=t.ou_uuid AND n.role_id=t.role_id
                  AND n.assigned_when=t.assigned_when AND n.assigned_by_user_id=t.assigned_by_user_id;

-- 9) Læg FK og indeks på exclusions
ALTER TABLE history_ou_role_assignment_exclusions
    ADD CONSTRAINT fk_excl_assignment
        FOREIGN KEY (assignment_id)
            REFERENCES history_ou_role_assignments(id)
            ON DELETE CASCADE;

CREATE INDEX ix_excl_assignment ON history_ou_role_assignment_exclusions (assignment_id);
CREATE INDEX ix_excl_type ON history_ou_role_assignment_exclusions (exclusion_type);

-- 10) Ryd op
DROP TABLE history_ou_role_assignments_old;
DROP TABLE stage_ou_role_assignments;

ALTER TABLE history_ou_role_assignments
    DROP INDEX uq_dedupe_hash,
    DROP COLUMN dedupe_hash;

-- Commit og gendan settings
COMMIT;
SET FOREIGN_KEY_CHECKS=@old_fk_checks;
SET UNIQUE_CHECKS=@old_unique_checks;
SET AUTOCOMMIT=@old_autocommit;

-- =====================================================================
-- Done
-- =====================================================================