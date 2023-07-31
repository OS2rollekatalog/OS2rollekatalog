DROP VIEW IF EXISTS view_datatables_userroles;

GO

CREATE VIEW view_datatables_userroles AS (
	SELECT 
	    ur.id AS id,
	    ur.name AS name,
	    ur.description AS description,
	    its.id AS it_system_id,
	    its.name AS it_system_name,
	    its.system_type AS it_system_type,
	    ur.can_request AS can_request,
	    CASE WHEN pku.id IS NULL THEN 0 ELSE 1 END AS pending_sync,
	    CASE WHEN pku.failed IS NULL THEN 0 ELSE 1 END AS sync_failed,
	    ur.delegated_from_cvr AS delegated_from_cvr
	FROM user_roles ur
		JOIN it_systems its ON its.id = ur.it_system_id
	    LEFT JOIN pending_kombit_updates pku ON pku.user_role_id = ur.id
	WHERE
	    its.deleted = 0
);