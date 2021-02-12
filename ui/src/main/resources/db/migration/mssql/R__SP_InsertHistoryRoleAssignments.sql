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

  -- user roles from orgunits
	WITH cte
	AS
	(
		SELECT 
			our.id
			,o.uuid AS ou_uuid
			,o.uuid AS orig_ou_uuid
			,o.parent_uuid
			,o.name AS orig_ou_name
			,our.inherit
		FROM ous o
		JOIN ou_roles our ON our.ou_uuid = o.uuid-- AND our.inherit = 1

		WHERE 
			o.active = 1 AND our.inactive = 0

		UNION ALL

		SELECT 
			cte.id
			,o.uuid AS ou_uuid
			,cte.orig_ou_uuid
			,o.parent_uuid
			,cte.orig_ou_name
			,cte.inherit
		FROM ous o
		JOIN cte ON cte.ou_uuid = o.parent_uuid AND cte.inherit = 1
		WHERE 
			o.active = 1
	)
	INSERT INTO history_role_assignments (
		dato
		,user_uuid
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
		,u.uuid
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
	JOIN positions p ON p.ou_uuid = cte.ou_uuid
	JOIN users u ON u.uuid = p.user_uuid
	JOIN user_roles ur ON ur.id = our.role_id
	JOIN it_systems it ON it.id = ur.it_system_id
	WHERE our.inactive = 0;

	-- user roles through rolegroups from orgunits
	WITH cte
	AS
	(
		SELECT 
			ourg.id
			,o.uuid AS ou_uuid
			,o.uuid AS orig_ou_uuid
			,o.parent_uuid
			,o.name AS orig_ou_name
			,ourg.inherit
		FROM ous o
		JOIN ou_rolegroups ourg ON ourg.ou_uuid = o.uuid
		WHERE 
			o.active = 1 AND ourg.inactive = 0

		UNION ALL

		SELECT 
			cte.id
			,o.uuid AS ou_uuid
			,cte.orig_ou_uuid
			,o.parent_uuid
			,cte.orig_ou_name
			,cte.inherit
		FROM ous o
		JOIN cte ON cte.ou_uuid = o.parent_uuid AND cte.inherit = 1
		WHERE 
			o.active = 1
	)
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
	SELECT
		CURRENT_TIMESTAMP
		,u.uuid
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
	JOIN ous ou ON ou.uuid = cte.ou_uuid
	JOIN positions p ON p.ou_uuid = ou.uuid
	JOIN users u ON u.uuid = p.user_uuid
	JOIN rolegroup rg ON rg.id = ourg.rolegroup_id
	JOIN rolegroup_roles rgr ON rgr.rolegroup_id = ourg.rolegroup_id
	JOIN user_roles ur ON ur.id = rgr.role_id
	JOIN it_systems it ON it.id = ur.it_system_id
	WHERE ourg.inactive = 0;

  -- TODO: review PSO: the two new statements below --

  -- user roles through titles
  INSERT INTO history_role_assignments (
    dato, user_uuid,
    role_id, role_name, role_it_system_id, role_it_system_name, role_role_group,
    assigned_through_type, assigned_through_uuid, assigned_through_name,
    assigned_by_user_id, assigned_by_name, assigned_when)
  SELECT CURRENT_TIMESTAMP, u.uuid,
    ur.id, ur.name, it.id, it.name, NULL,
    'TITLE', t.uuid, CONCAT_WS('', t.name, CONCAT(' (', ou.name, ')')),
    tr.assigned_by_user_id, tr.assigned_by_name, tr.assigned_timestamp
  FROM title_roles tr
  LEFT JOIN title_roles_ous trou ON trou.title_roles_id = tr.id
  JOIN titles t ON t.uuid = tr.title_uuid
  JOIN positions p ON p.title_uuid = t.uuid AND (trou.ou_uuid IS NULL OR trou.ou_uuid = p.ou_uuid)
  JOIN ous ou ON ou.uuid = p.ou_uuid
  JOIN users u ON u.uuid = p.user_uuid
  JOIN user_roles ur ON ur.id = tr.role_id
  JOIN it_systems it ON it.id = ur.it_system_id
  WHERE tr.inactive = 0;

  
  -- role groups through titles
  INSERT INTO history_role_assignments (
    dato, user_uuid,
    role_id, role_name, role_it_system_id, role_it_system_name, role_role_group,
    assigned_through_type, assigned_through_uuid, assigned_through_name,
    assigned_by_user_id, assigned_by_name, assigned_when)
  SELECT CURRENT_TIMESTAMP, u.uuid,
    ur.id, ur.name, it.id, it.name, rg.name,
    'TITLE', t.uuid, CONCAT_WS('', t.name, CONCAT(' (', ou.name, ')')),
    trg.assigned_by_user_id, trg.assigned_by_name, trg.assigned_timestamp
  FROM title_rolegroups trg
  JOIN rolegroup rg ON trg.rolegroup_id = rg.id
  LEFT JOIN title_rolegroups_ous trgou ON trgou.title_rolegroups_id = trg.id
  JOIN titles t ON t.uuid = trg.title_uuid
  JOIN positions p ON p.title_uuid = t.uuid AND (trgou.ou_uuid IS NULL OR trgou.ou_uuid = p.ou_uuid)
  JOIN ous ou ON ou.uuid = p.ou_uuid
  JOIN users u ON u.uuid = p.user_uuid
  JOIN rolegroup_roles rgr ON rgr.rolegroup_id = trg.rolegroup_id
  JOIN user_roles ur ON ur.id = rgr.role_id
  JOIN it_systems it ON it.id = ur.it_system_id
  WHERE trg.inactive = 0;
	
END
GO