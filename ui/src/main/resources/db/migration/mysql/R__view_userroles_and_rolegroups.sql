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
        urp.effective_requester_permission AS effective_requester_permission,
        urp.effective_approver_permission AS effective_approver_permission,
        IF(pku.id IS NULL, FALSE, TRUE) AS pending_sync,
        IF(pku.failed IS NULL, FALSE, pku.failed) AS sync_failed,
        ur.delegated_from_cvr AS delegated_from_cvr,
        ur.read_only AS read_only,
        ur.user_only AS user_only,
        -- NULL = no OU filter, '' = filter enabled without OUs (allows nothing). The join tables
        -- can retain stale OUs after the filter is disabled, so they are only exposed when enabled.
        CASE WHEN ur.ou_filter_enabled = TRUE THEN COALESCE(GROUP_CONCAT(DISTINCT our.ou_uuid), '') END as org_unit_filter_uuids,
        CASE WHEN its.ou_filter_enabled = TRUE THEN COALESCE(GROUP_CONCAT(DISTINCT itsou.ou_uuid), '') END as it_system_org_unit_filter_uuids,
        GROUP_CONCAT(DISTINCT rg.name SEPARATOR ', ') AS role_within_role_group
    FROM user_roles ur
             JOIN it_systems its ON its.id = ur.it_system_id
             JOIN view_user_role_permissions urp ON urp.user_role_id = ur.id
             LEFT JOIN pending_kombit_updates pku ON pku.user_role_id = ur.id
             LEFT JOIN ous_user_roles our ON ur.id = our.user_roles_id
             LEFT JOIN ous_itsystems itsou ON its.id = itsou.itsystem_id
             LEFT JOIN rolegroup_roles rgr ON rgr.role_id = ur.id
             LEFT JOIN rolegroup rg ON rg.id = rgr.rolegroup_id
    WHERE its.deleted = FALSE
    GROUP BY ur.id

    UNION ALL

    -- Role Groups
    -- The OU filter is ous_rolegroup (not ou_rolegroups, which holds assignments), and it includes
    -- the descendants of its OUs.
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
        rgp.effective_requester_permission AS effective_requester_permission,
        rgp.effective_approver_permission AS effective_approver_permission,
        FALSE AS pending_sync,
        FALSE AS sync_failed,
        NULL AS delegated_from_cvr,
        FALSE AS read_only,
        rg.user_only AS user_only,
        CASE WHEN rg.ou_filter_enabled = TRUE THEN COALESCE(GROUP_CONCAT(DISTINCT rgou.ou_uuid), '') END as org_unit_filter_uuids,
        NULL as it_system_org_unit_filter_uuids,
        GROUP_CONCAT(DISTINCT ur.name SEPARATOR ', ') AS role_within_role_group
    FROM rolegroup rg
             JOIN view_role_group_permissions rgp ON rgp.role_group_id = rg.id
             LEFT JOIN ous_rolegroup rgou ON rgou.rolegroup_id = rg.id
             LEFT JOIN rolegroup_roles rgr ON rgr.rolegroup_id = rg.id
             LEFT JOIN user_roles ur ON ur.id = rgr.role_id
    GROUP BY rg.id
);
