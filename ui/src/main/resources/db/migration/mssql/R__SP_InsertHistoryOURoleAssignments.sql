-- Stored procedure for populating history_ou_role_assignments table
-- should only be called once per day like this
--
-- EXEC SP_InsertHistoryOURoleAssignments;

CREATE OR ALTER PROC SP_InsertHistoryOURoleAssignments
AS
BEGIN
	INSERT INTO history_ou_role_assignments (
		dato
		,ou_uuid
		,role_id
		,role_name
		,role_it_system_id
		,role_it_system_name
		,role_role_group
		,assigned_through_type
		,assigned_through_uuid
		,assigned_through_name
		,assigned_by_user_id
		,assigned_by_name
		,assigned_when
	)
	-- user roles from direct assignments
	SELECT 
		CURRENT_TIMESTAMP
		,o.uuid
		,ur.id
		,ur.name
		,it.id
		,it.name
		, NULL
		,'DIRECT'
		,NULL
		,NULL
		,our.assigned_by_user_id
		,our.assigned_by_name
		,our.assigned_timestamp
	FROM ou_roles our
		JOIN ous o ON o.uuid = our.ou_uuid
		JOIN user_roles ur ON ur.id = our.role_id
		JOIN it_systems it ON it.id = ur.it_system_id
	WHERE 
		our.inherit = 0
		AND our.inactive = 0
		AND o.active = 1

	UNION ALL

	-- user roles through rolegroups from direct assignments
	SELECT 
		CURRENT_TIMESTAMP
		,o.uuid
		,ur.id
		,ur.name
		,it.id
		,it.name
		,rg.name
		,'DIRECT'
		,NULL
		,NULL
		,ourg.assigned_by_user_id
		,ourg.assigned_by_name
		,ourg.assigned_timestamp
		FROM ou_rolegroups ourg
		JOIN ous o ON o.uuid = ourg.ou_uuid
		JOIN rolegroup rg ON ourg.rolegroup_id = rg.id
		JOIN rolegroup_roles rgr ON rgr.rolegroup_id = ourg.rolegroup_id
		JOIN user_roles ur ON ur.id = rgr.role_id
		JOIN it_systems it ON it.id = ur.it_system_id
	WHERE 
		ourg.inherit = 0 
		AND ourg.inactive = 0
		AND o.active = 1;

	-- user roles from orgunits (inherited)
	WITH cte
	AS
	(
		SELECT 
			our.id
			,o.uuid AS ou_uuid
			,o.uuid AS orig_ou_uuid
			,o.parent_uuid
			,o.name AS orig_ou_name
		FROM ous o
		JOIN ou_roles our ON our.ou_uuid = o.uuid AND our.inherit = 1
		WHERE 
			o.active = 1

		UNION ALL

		SELECT 
			cte.id
			,o.uuid AS ou_uuid
			,cte.orig_ou_uuid
			,o.parent_uuid
			,cte.orig_ou_name
		FROM ous o
		JOIN cte ON cte.ou_uuid = o.parent_uuid
		WHERE 
			o.active = 1
	)
	INSERT INTO history_ou_role_assignments (
		dato
		,ou_uuid
		,role_id
		,role_name
		,role_it_system_id
		,role_it_system_name
		,assigned_through_type
		,assigned_through_uuid
		,assigned_through_name
		,assigned_by_user_id
		,assigned_by_name
		,assigned_when
	)
	SELECT
		CURRENT_TIMESTAMP
		,cte.ou_uuid
		,ur.id
		,ur.name
		,it.id
		,it.name
		,'ORGUNIT'
		,orig_ou_uuid
		,orig_ou_name
		,our.assigned_by_user_id
		,our.assigned_by_name
		,our.assigned_timestamp
	FROM cte
	JOIN ou_roles our ON our.id = cte.id
	JOIN ous ou ON ou.uuid = our.ou_uuid
	JOIN user_roles ur ON ur.id = our.role_id
	JOIN it_systems it ON it.id = ur.it_system_id
	WHERE our.inactive = 0;

	-- user roles through rolegroups from orgunits (inherited)
	WITH cte
	AS
	(
		SELECT 
			ourg.id
			,o.uuid AS ou_uuid
			,o.uuid AS orig_ou_uuid
			,o.parent_uuid
			,o.name AS orig_ou_name
		FROM ous o
		JOIN ou_rolegroups ourg ON ourg.ou_uuid = o.uuid AND ourg.inherit = 1
		WHERE 
			o.active = 1 AND ourg.inactive = 0

		UNION ALL

		SELECT 
			cte.id
			,o.uuid AS ou_uuid
			,cte.orig_ou_uuid
			,o.parent_uuid
			,cte.orig_ou_name
		FROM ous o
		JOIN cte ON cte.ou_uuid = o.parent_uuid
		WHERE 
			o.active = 1
	)
	INSERT INTO history_ou_role_assignments (
		dato
		,ou_uuid
		,role_id
		,role_name
		,role_it_system_id
		,role_it_system_name
		,role_role_group
		,assigned_through_type
		,assigned_through_uuid
		,assigned_through_name
		,assigned_by_user_id
		,assigned_by_name
		,assigned_when
	)
	SELECT
		CURRENT_TIMESTAMP
		,cte.ou_uuid
		,ur.id
		,ur.name
		,it.id
		,it.name
		,rg.name
		,'ORGUNIT'
		,orig_ou_uuid
		,orig_ou_name
		,ourg.assigned_by_user_id
		,ourg.assigned_by_name
		,ourg.assigned_timestamp
	FROM cte
	JOIN ou_rolegroups ourg ON ourg.id = cte.id
	JOIN rolegroup rg ON rg.id = ourg.rolegroup_id
	JOIN rolegroup_roles rgr ON rgr.rolegroup_id = ourg.rolegroup_id
	JOIN user_roles ur ON ur.id = rgr.role_id
	JOIN it_systems it ON it.id = ur.it_system_id
	WHERE ourg.inactive = 0
END
GO