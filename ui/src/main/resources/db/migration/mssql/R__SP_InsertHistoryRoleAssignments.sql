-- Stored procedure for populating history_role_assignments table
-- should only be called once per day like this
--
-- EXEC SP_InsertHistoryRoleAssignments;

CREATE OR ALTER PROC SP_InsertHistoryRoleAssignments
AS
BEGIN

	INSERT INTO history_role_assignments (
		dato
		,user_uuid
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
		,u.uuid
		,ur.id
		,ur.name
		,it.id
		,it.name
		,NULL
		,'DIRECT'
		,NULL
		,NULL
		,urm.assigned_by_user_id
		,urm.assigned_by_name
		,urm.assigned_timestamp
	FROM user_roles_mapping urm
	JOIN users u ON u.uuid = urm.user_uuid
	JOIN user_roles ur ON ur.id = urm.role_id
	JOIN it_systems it ON it.id = ur.it_system_id
	WHERE urm.inactive = 0
	UNION ALL
	-- user roles through rolegroups from direct assignments
	SELECT 
		CURRENT_TIMESTAMP
		,u.uuid
		,ur.id
		,ur.name
		,it.id
		,it.name
		,rg.name
		,'DIRECT'
		,NULL
		,NULL
		,urg.assigned_by_user_id
		,urg.assigned_by_name
		,urg.assigned_timestamp
	FROM user_rolegroups urg
	JOIN rolegroup rg ON urg.rolegroup_id = rg.id
	JOIN users u ON u.uuid = urg.user_uuid
	JOIN rolegroup_roles rgr ON rgr.rolegroup_id = urg.rolegroup_id
	JOIN user_roles ur ON ur.id = rgr.role_id
	JOIN it_systems it ON it.id = ur.it_system_id
	WHERE urg.inactive = 0
	UNION ALL
	-- user roles from position assignments
	SELECT 
		CURRENT_TIMESTAMP
		,u.uuid
		,ur.id
		,ur.name
		,it.id
		,it.name
		,NULL
		,'POSITION'
		,ou.uuid
		,CONCAT(p.name, ' i ', ou.name)
		,pr.assigned_by_user_id
		,pr.assigned_by_name
		,pr.assigned_timestamp
	FROM position_roles pr
	JOIN positions p on p.id = pr.position_id
	JOIN users u ON u.uuid = p.user_uuid
	JOIN ous ou ON ou.uuid = p.ou_uuid
	JOIN user_roles ur ON ur.id = pr.role_id
	JOIN it_systems it ON it.id = ur.it_system_id
	WHERE pr.inactive = 0
	UNION ALL 
	-- user roles through rolegroup from position assignments
	SELECT
		CURRENT_TIMESTAMP
		,u.uuid
		,ur.id
		,ur.name
		,it.id
		,it.name
		,rg.name
		,'POSITION'
		,ou.uuid
		,CONCAT(p.name, ' i ', ou.name)
		,prg.assigned_by_user_id
		,prg.assigned_by_name
		,prg.assigned_timestamp
	FROM position_rolegroups prg
	JOIN rolegroup rg ON prg.rolegroup_id = rg.id
	JOIN positions p on p.id = prg.position_id
	JOIN users u ON u.uuid = p.user_uuid
	JOIN ous ou ON ou.uuid = p.ou_uuid
	JOIN rolegroup_roles rgr ON rgr.rolegroup_id = prg.rolegroup_id
	JOIN user_roles ur ON ur.id = rgr.role_id
	JOIN it_systems it ON it.id = ur.it_system_id
	WHERE prg.inactive = 0;
	
END
GO