BEGIN TRANSACTION;

---------------------------------------------------------------------
-- 1) Rename the original table so we can reuse the name
---------------------------------------------------------------------
IF OBJECT_ID(N'history_ou_role_assignments_old', N'U') IS NOT NULL
    DROP TABLE history_ou_role_assignments_old;

EXEC sp_rename 'history_ou_role_assignments', 'history_ou_role_assignments_old', 'OBJECT';

---------------------------------------------------------------------
-- 1.1) Create new history table combining all others
---------------------------------------------------------------------
IF OBJECT_ID(N'history_ou_role_assignments', N'U') IS NOT NULL
    DROP TABLE history_ou_role_assignments;

CREATE TABLE history_ou_role_assignments (
    id                   BIGINT IDENTITY(1,1) NOT NULL,
    dato                 DATE NOT NULL,
    ou_uuid              VARCHAR(36) NOT NULL,
    role_id              BIGINT NOT NULL,
    role_name            VARCHAR(128) NOT NULL,
    role_it_system_id    BIGINT NOT NULL,
    role_it_system_name  VARCHAR(64) NOT NULL,
    role_role_group      VARCHAR(128) NULL,
    assigned_through_type VARCHAR(64) NULL,
    assigned_through_uuid VARCHAR(36) NULL,
    assigned_through_name VARCHAR(512) NULL,
    role_role_group_id   BIGINT NULL,
    inherit              BIT NULL,
    assigned_by_user_id  VARCHAR(255) NOT NULL,
    assigned_by_name     VARCHAR(255) NOT NULL,
    assigned_when        DATETIME2(0) NOT NULL,
    start_date           DATE NULL,
    stop_date            DATE NULL,
    CONSTRAINT PK_history_ou_role_assignments PRIMARY KEY (id)
);

CREATE INDEX IX_history_ou_role_assignments_dato
    ON history_ou_role_assignments (dato);

---------------------------------------------------------------------
-- 1.2) Create a table for exclusions
---------------------------------------------------------------------
IF OBJECT_ID(N'history_ou_role_assignment_exclusions', N'U') IS NOT NULL
    DROP TABLE history_ou_role_assignment_exclusions;

CREATE TABLE history_ou_role_assignment_exclusions (
    id             BIGINT IDENTITY(1,1) NOT NULL,
    assignment_id  BIGINT NOT NULL,
    exclusion_type VARCHAR(20) NOT NULL,
    user_uuids     NVARCHAR(MAX) NULL,
    title_uuids    NVARCHAR(MAX) NULL,
    CONSTRAINT PK_history_ou_role_assignment_exclusions PRIMARY KEY (id),
    CONSTRAINT FK_history_excl_assignment
        FOREIGN KEY (assignment_id)
        REFERENCES history_ou_role_assignments(id)
        ON DELETE CASCADE,
    CONSTRAINT CK_history_excl_type
        CHECK (exclusion_type IN ('excepted_users','titles','negative_titles'))
);

---------------------------------------------------------------------
-- 2.1) Insert unique assignments from all four tables
---------------------------------------------------------------------
INSERT INTO history_ou_role_assignments (
    dato, ou_uuid, role_id, role_name, role_it_system_id, role_it_system_name,
    role_role_group, assigned_through_type, assigned_through_uuid, assigned_through_name,
    role_role_group_id, inherit, assigned_by_user_id, assigned_by_name,
    assigned_when, start_date, stop_date
)
SELECT DISTINCT
    a.dato, a.ou_uuid, a.role_id, a.role_name, a.role_it_system_id, a.role_it_system_name,
    a.role_role_group, a.assigned_through_type, a.assigned_through_uuid, a.assigned_through_name,
    a.role_role_group_id, a.inherit, a.assigned_by_user_id, a.assigned_by_name,
    a.assigned_when, a.start_date, a.stop_date
FROM (
    SELECT
        dato, ou_uuid, role_id, role_name, role_it_system_id, role_it_system_name,
        role_role_group,
        CAST(NULL AS VARCHAR(64))  AS assigned_through_type,
        CAST(NULL AS VARCHAR(36))  AS assigned_through_uuid,
        CAST(NULL AS VARCHAR(512)) AS assigned_through_name,
        role_role_group_id,
        CAST(NULL AS BIT)          AS inherit,
        assigned_by_user_id, assigned_by_name,
        CAST(assigned_when AS DATETIME2(0)) AS assigned_when,
        start_date, stop_date
    FROM history_role_assignment_excepted_users

    UNION ALL

    SELECT
        dato, ou_uuid, role_id, role_name, role_it_system_id, role_it_system_name,
        role_role_group, assigned_through_type, assigned_through_uuid, assigned_through_name,
        role_role_group_id, inherit, assigned_by_user_id, assigned_by_name,
        CAST(assigned_when AS DATETIME2(0)) AS assigned_when,
        start_date, stop_date
    FROM history_role_assignment_negative_titles

    UNION ALL

    SELECT
        dato, ou_uuid, role_id, role_name, role_it_system_id, role_it_system_name,
        role_role_group, assigned_through_type, assigned_through_uuid, assigned_through_name,
        role_role_group_id, inherit, assigned_by_user_id, assigned_by_name,
        CAST(assigned_when AS DATETIME2(0)) AS assigned_when,
        start_date, stop_date
    FROM history_role_assignment_titles

    UNION ALL

    SELECT
        dato, ou_uuid, role_id, role_name, role_it_system_id, role_it_system_name,
        role_role_group, assigned_through_type, assigned_through_uuid, assigned_through_name,
        role_role_group_id, inherit, assigned_by_user_id, assigned_by_name,
        CAST(assigned_when AS DATETIME2(0)) AS assigned_when,
        start_date, stop_date
    FROM history_ou_role_assignments_old
) AS a;

---------------------------------------------------------------------
-- 2.2) Insert exclusions from excepted_users
---------------------------------------------------------------------
INSERT INTO history_ou_role_assignment_exclusions (
    assignment_id, exclusion_type, user_uuids
)
SELECT newa.id, 'excepted_users', CAST(old.user_uuids AS NVARCHAR(MAX))
FROM history_role_assignment_excepted_users AS old
INNER JOIN history_ou_role_assignments AS newa
    ON newa.dato                = old.dato
   AND newa.ou_uuid             = old.ou_uuid
   AND newa.role_id             = old.role_id
   AND newa.assigned_when       = CAST(old.assigned_when AS DATETIME2(0))
   AND newa.assigned_by_user_id = old.assigned_by_user_id;

---------------------------------------------------------------------
-- 2.3) Insert exclusions from negative_titles
---------------------------------------------------------------------
INSERT INTO history_ou_role_assignment_exclusions (
    assignment_id, exclusion_type, title_uuids
)
SELECT newa.id, 'negative_titles', CAST(old.title_uuids AS NVARCHAR(MAX))
FROM history_role_assignment_negative_titles AS old
INNER JOIN history_ou_role_assignments AS newa
    ON newa.dato                = old.dato
   AND newa.ou_uuid             = old.ou_uuid
   AND newa.role_id             = old.role_id
   AND newa.assigned_when       = CAST(old.assigned_when AS DATETIME2(0))
   AND newa.assigned_by_user_id = old.assigned_by_user_id;

---------------------------------------------------------------------
-- 2.4) Insert exclusions from titles
---------------------------------------------------------------------
INSERT INTO history_ou_role_assignment_exclusions (
    assignment_id, exclusion_type, title_uuids
)
SELECT newa.id, 'titles', CAST(old.title_uuids AS NVARCHAR(MAX))
FROM history_role_assignment_titles AS old
INNER JOIN history_ou_role_assignments AS newa
    ON newa.dato                = old.dato
   AND newa.ou_uuid             = old.ou_uuid
   AND newa.role_id             = old.role_id
   AND newa.assigned_when       = CAST(old.assigned_when AS DATETIME2(0))
   AND newa.assigned_by_user_id = old.assigned_by_user_id;

---------------------------------------------------------------------
-- 3) Delete old assignments table
---------------------------------------------------------------------
DROP TABLE history_ou_role_assignments_old;

COMMIT;
