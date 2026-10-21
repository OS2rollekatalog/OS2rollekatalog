-- The single definition of how request/approve permissions are inherited:
-- user role -> IT system -> global, and role group -> global.
-- Requester: an empty or INHERIT permission inherits, and an empty global setting means NONE.
-- Approver: a NULL or INHERIT permission inherits, and an empty permission means nobody (only admins).
-- The "0" in the file name makes Flyway create these views before the views that depend on them.

CREATE OR REPLACE VIEW view_global_rolerequest_permissions AS
SELECT
    COALESCE(NULLIF(MAX(CASE WHEN s.setting_key = 'allowedrequesters' THEN s.setting_value END), ''), 'NONE') AS effective_requester_permission,
    COALESCE(MAX(CASE WHEN s.setting_key = 'allowedrapprovers' THEN s.setting_value END), '') AS effective_approver_permission
FROM setting s
WHERE s.setting_key IN ('allowedrequesters', 'allowedrapprovers');

CREATE OR REPLACE VIEW view_it_system_permissions AS
SELECT
    its.id AS it_system_id,
    its.requester_permission AS requester_permission,
    CASE
        WHEN its.requester_permission IS NULL OR its.requester_permission IN ('', 'INHERIT')
            THEN g.effective_requester_permission
        ELSE its.requester_permission
    END AS effective_requester_permission,
    its.approver_permission AS approver_permission,
    CASE
        WHEN its.approver_permission IS NULL OR its.approver_permission = 'INHERIT'
            THEN g.effective_approver_permission
        ELSE its.approver_permission
    END AS effective_approver_permission
FROM it_systems its
CROSS JOIN view_global_rolerequest_permissions g;

CREATE OR REPLACE VIEW view_user_role_permissions AS
SELECT
    ur.id AS user_role_id,
    ur.it_system_id AS it_system_id,
    ur.read_only AS read_only,
    ur.requester_permission AS requester_permission,
    CASE
        WHEN ur.requester_permission IS NULL OR ur.requester_permission IN ('', 'INHERIT')
            THEN itsp.effective_requester_permission
        ELSE ur.requester_permission
    END AS effective_requester_permission,
    ur.approver_permission AS approver_permission,
    CASE
        WHEN ur.approver_permission IS NULL OR ur.approver_permission = 'INHERIT'
            THEN itsp.effective_approver_permission
        ELSE ur.approver_permission
    END AS effective_approver_permission
FROM user_roles ur
JOIN view_it_system_permissions itsp ON itsp.it_system_id = ur.it_system_id;

CREATE OR REPLACE VIEW view_role_group_permissions AS
SELECT
    rg.id AS role_group_id,
    rg.requester_permission AS requester_permission,
    CASE
        WHEN rg.requester_permission IS NULL OR rg.requester_permission IN ('', 'INHERIT')
            THEN g.effective_requester_permission
        ELSE rg.requester_permission
    END AS effective_requester_permission,
    rg.approver_permission AS approver_permission,
    CASE
        WHEN rg.approver_permission IS NULL OR rg.approver_permission = 'INHERIT'
            THEN g.effective_approver_permission
        ELSE rg.approver_permission
    END AS effective_approver_permission
FROM rolegroup rg
CROSS JOIN view_global_rolerequest_permissions g;
