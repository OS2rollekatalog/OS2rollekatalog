-- Stored procedure for populating history_title_role_assignments table
-- should only be called once per day like this
--
-- EXEC SP_InsertHistoryTitleRoleAssignments();

CREATE OR ALTER PROC SP_InsertHistoryTitleRoleAssignments
AS
BEGIN
	
  -- user roles
  INSERT INTO history_title_role_assignments (
    dato, title_uuid,
    role_id, role_name, role_it_system_id, role_it_system_name, role_role_group,
    assigned_through_type, assigned_through_uuid, assigned_through_name,
    assigned_by_user_id, assigned_by_name, assigned_when)
  SELECT CURRENT_TIMESTAMP, t.uuid,
    ur.id, ur.name, it.id, it.name, NULL,
    'TITLE', ou.uuid, CONCAT_WS('', t.name, CONCAT(' (', ou.name, ')')),
    tr.assigned_by_user_id, tr.assigned_by_name, tr.assigned_timestamp
  FROM title_roles tr
  JOIN titles t ON t.uuid = tr.title_uuid
  JOIN user_roles ur ON ur.id = tr.role_id
  JOIN it_systems it ON it.id = ur.it_system_id
  LEFT JOIN title_roles_ous trou ON trou.title_roles_id = tr.id
  LEFT JOIN ous ou ON ou.uuid = trou.ou_uuid
  WHERE tr.inactive = 0;

  -- user roles through rolegroups
  INSERT INTO history_title_role_assignments (
    dato, title_uuid,
    role_id, role_name, role_it_system_id, role_it_system_name, role_role_group,
    assigned_through_type, assigned_through_uuid, assigned_through_name,
    assigned_by_user_id, assigned_by_name, assigned_when)
  SELECT CURRENT_TIMESTAMP, t.uuid,
    ur.id, ur.name, it.id, it.name, rg.name,
    'TITLE', ou.uuid, CONCAT_WS('', t.name, CONCAT(' (', ou.name, ')')),
    trg.assigned_by_user_id, trg.assigned_by_name, trg.assigned_timestamp
  FROM title_rolegroups trg
  JOIN rolegroup rg ON trg.rolegroup_id = rg.id
  JOIN titles t ON t.uuid = trg.title_uuid
  JOIN rolegroup_roles rgr ON rgr.rolegroup_id = trg.rolegroup_id
  JOIN user_roles ur ON ur.id = rgr.role_id
  JOIN it_systems it ON it.id = ur.it_system_id
  LEFT JOIN title_rolegroups_ous trgou ON trgou.title_rolegroups_id = trg.id
  LEFT JOIN ous ou ON ou.uuid = trgou.ou_uuid
  WHERE trg.inactive = 0;

END
GO
