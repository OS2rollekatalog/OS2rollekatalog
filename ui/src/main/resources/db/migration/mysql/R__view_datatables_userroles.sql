CREATE OR REPLACE VIEW view_datatables_userroles AS (
	SELECT 
	    ur.id AS id,
	    ur.name AS name,
	    ur.description AS description,
	    its.id AS it_system_id,
	    its.name AS it_system_name,
	    its.system_type AS it_system_type,
 	    ur.can_request AS can_request,
	    IF(pku.id IS NULL, FALSE, TRUE) AS pending_sync,
	    IF(pku.failed IS NULL, FALSE, pku.failed) AS sync_failed,
	    ur.delegated_from_cvr AS delegated_from_cvr
	FROM user_roles ur
		JOIN it_systems its ON its.id = ur.it_system_id
	    LEFT JOIN pending_kombit_updates pku ON pku.user_role_id = ur.id
	WHERE
	    its.deleted = FALSE
);