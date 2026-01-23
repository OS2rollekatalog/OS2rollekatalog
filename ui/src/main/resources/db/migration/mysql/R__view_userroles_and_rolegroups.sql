CREATE OR REPLACE VIEW view_datatables_combined_roles AS (
    -- User Roles
    SELECT
        ur.id AS id,
        'userRole' AS type,
        ur.name AS name,
        ur.description AS description,
        its.id AS it_system_id,
        its.name AS it_system_name,
        its.system_type AS it_system_type,
        its.identifier AS it_system_identifier,
        ur.requester_permission AS requester_permission,
        ur.approver_permission AS approver_permission,
        TRIM(BOTH ',' FROM
             CONCAT(
                     CASE
                         WHEN ur.requester_permission NOT LIKE '%INHERIT%'
                             THEN CONCAT(ur.requester_permission, ',')
                         WHEN ur.requester_permission IS NOT NULL
                             THEN CONCAT(
                                 TRIM(BOTH ',' FROM
                                      REGEXP_REPLACE(ur.requester_permission, '(^|,)INHERIT(,|$)', ',')
                                 ),
                                 ','
                                  )
                         ELSE ''
                         END,
                     CASE
                         WHEN ur.requester_permission LIKE '%INHERIT%'
                             AND its.requester_permission IS NOT NULL
                             AND its.requester_permission NOT LIKE '%INHERIT%'
                             THEN CONCAT(its.requester_permission, ',')
                         ELSE ''
                         END,
                     CASE
                         WHEN ur.requester_permission LIKE '%INHERIT%'
                             AND (its.requester_permission IS NULL OR its.requester_permission LIKE '%INHERIT%')
                             THEN COALESCE(
                                 (SELECT setting_value FROM setting WHERE setting_key = 'allowedrequesters'),
                                 ''
                                  )
                         ELSE ''
                         END
             )
        ) AS effective_requester_permission,
        TRIM(BOTH ',' FROM
             CONCAT(
                     CASE
                         WHEN ur.approver_permission NOT LIKE '%INHERIT%'
                             THEN CONCAT(ur.approver_permission, ',')
                         WHEN ur.approver_permission IS NOT NULL
                             THEN CONCAT(
                                 TRIM(BOTH ',' FROM
                                      REGEXP_REPLACE(ur.approver_permission, '(^|,)INHERIT(,|$)', ',')
                                 ),
                                 ','
                                  )
                         ELSE ''
                         END,
                     CASE
                         WHEN ur.approver_permission LIKE '%INHERIT%'
                             AND its.approver_permission IS NOT NULL
                             AND its.approver_permission NOT LIKE '%INHERIT%'
                             THEN CONCAT(its.approver_permission, ',')
                         WHEN ur.approver_permission LIKE '%INHERIT%'
                             AND its.approver_permission IS NOT NULL
                             AND its.approver_permission LIKE '%INHERIT%'
                             THEN CONCAT(
                                 TRIM(BOTH ',' FROM
                                      REGEXP_REPLACE(its.approver_permission, '(^|,)INHERIT(,|$)', ',')
                                 ),
                                 ','
                                  )
                         ELSE ''
                         END,
                     CASE
                         WHEN ur.approver_permission LIKE '%INHERIT%'
                             AND (its.approver_permission IS NULL OR its.approver_permission LIKE '%INHERIT%')
                             THEN COALESCE(
                                 (SELECT setting_value FROM setting WHERE setting_key = 'allowedrapprovers'),
                                 ''
                                  )
                         ELSE ''
                         END
             )
        ) AS effective_approver_permission,
        IF(pku.id IS NULL, FALSE, TRUE) AS pending_sync,
        IF(pku.failed IS NULL, FALSE, pku.failed) AS sync_failed,
        ur.delegated_from_cvr AS delegated_from_cvr,
        ur.read_only AS read_only,
        ur.user_only AS user_only,
        GROUP_CONCAT(DISTINCT our.ou_uuid) as org_unit_filter_uuids,
        GROUP_CONCAT(DISTINCT itsou.ou_uuid) as it_system_org_unit_filter_uuids,
        GROUP_CONCAT(DISTINCT rg.name SEPARATOR ', ') AS role_within_role_group
    FROM user_roles ur
             JOIN it_systems its ON its.id = ur.it_system_id
             LEFT JOIN pending_kombit_updates pku ON pku.user_role_id = ur.id
             LEFT JOIN ous_user_roles our ON ur.id = our.user_roles_id
             LEFT JOIN ous_itsystems itsou ON its.id = itsou.itsystem_id
             LEFT JOIN rolegroup_roles rgr ON rgr.role_id = ur.id
             LEFT JOIN rolegroup rg ON rg.id = rgr.rolegroup_id
    WHERE its.deleted = FALSE
    GROUP BY ur.id

    UNION ALL

    -- Role Groups
    SELECT
        rg.id AS id,
        'roleGroup' AS type,
        rg.name AS name,
        rg.description AS description,
        NULL AS it_system_id,
        NULL AS it_system_name,
        NULL AS it_system_type,
        NULL AS it_system_identifier,
        rg.requester_permission AS requester_permission,
        rg.approver_permission AS approver_permission,
        TRIM(BOTH ',' FROM
             CASE
                 WHEN rg.requester_permission NOT LIKE '%INHERIT%'
                     THEN rg.requester_permission
                 WHEN rg.requester_permission IS NOT NULL
                     THEN COALESCE(
                         (SELECT setting_value FROM setting WHERE setting_key = 'allowedrequesters'),
                         TRIM(BOTH ',' FROM
                              REGEXP_REPLACE(rg.requester_permission, '(^|,)INHERIT(,|$)', ',')
                         )
                          )
                 ELSE COALESCE(
                         (SELECT setting_value FROM setting WHERE setting_key = 'allowedrequesters'),
                         ''
                      )
                 END
        ) AS effective_requester_permission,
        TRIM(BOTH ',' FROM
             CASE
                 WHEN rg.approver_permission NOT LIKE '%INHERIT%'
                     THEN rg.approver_permission
                 WHEN rg.approver_permission IS NOT NULL
                     THEN COALESCE(
                         (SELECT setting_value FROM setting WHERE setting_key = 'allowedrapprovers'),
                         TRIM(BOTH ',' FROM
                              REGEXP_REPLACE(rg.approver_permission, '(^|,)INHERIT(,|$)', ',')
                         )
                          )
                 ELSE COALESCE(
                         (SELECT setting_value FROM setting WHERE setting_key = 'allowedrapprovers'),
                         ''
                      )
                 END
        ) AS effective_approver_permission,
        FALSE AS pending_sync,
        FALSE AS sync_failed,
        NULL AS delegated_from_cvr,
        FALSE AS read_only,
        rg.user_only AS user_only,
        GROUP_CONCAT(DISTINCT orgu.ou_uuid) as org_unit_filter_uuids,
        NULL as it_system_org_unit_filter_uuids,
        GROUP_CONCAT(DISTINCT ur.name SEPARATOR ', ') AS role_within_role_group
    FROM rolegroup rg
             LEFT JOIN ou_rolegroups orgu ON rg.id = orgu.rolegroup_id
             LEFT JOIN rolegroup_roles rgr ON rgr.rolegroup_id = rg.id
             LEFT JOIN user_roles ur ON ur.id = rgr.role_id
    GROUP BY rg.id
);