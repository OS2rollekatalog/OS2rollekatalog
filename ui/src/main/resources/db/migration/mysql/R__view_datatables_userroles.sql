CREATE OR REPLACE VIEW view_datatables_userroles AS (
    SELECT
        ur.id AS id,
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
        ur.allow_postponing AS allow_postponing,
        ur.read_only AS read_only,
        -- exposed so the assignment lists can filter out roles in read-only AD it systems, which
        -- are never provisioned and therefore cannot be assigned - see UserRoleService.assignmentBlockedReason
        its.readonly AS it_system_readonly,
        -- NULL = no OU filter, '' = filter enabled without OUs (allows nothing). The join tables
        -- can retain stale OUs after the filter is disabled, so they are only exposed when enabled.
        CASE WHEN ur.ou_filter_enabled = TRUE THEN COALESCE(GROUP_CONCAT(DISTINCT our.ou_uuid), '') END as org_unit_filter_uuids,
        CASE WHEN its.ou_filter_enabled = TRUE THEN COALESCE(GROUP_CONCAT(DISTINCT itsou.ou_uuid), '') END as it_system_org_unit_filter_uuids
    FROM user_roles ur
             JOIN it_systems its ON its.id = ur.it_system_id
             JOIN view_user_role_permissions urp ON urp.user_role_id = ur.id
             LEFT JOIN pending_kombit_updates pku ON pku.user_role_id = ur.id
             LEFT JOIN ous_user_roles our ON ur.id = our.user_roles_id
             LEFT JOIN ous_itsystems itsou ON its.id = itsou.itsystem_id
    WHERE
        its.deleted = FALSE
    GROUP BY ur.id
);
