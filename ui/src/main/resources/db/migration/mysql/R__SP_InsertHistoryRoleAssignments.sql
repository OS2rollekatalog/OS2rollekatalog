-- Stored procedure for populating history_role_assignments table
-- should only be called once per day like this
--
-- CALL SP_InsertHistoryRoleAssignments();
--

DELIMITER $$
DROP PROCEDURE IF EXISTS SP_InsertHistoryRoleAssignments $$

-- user roles from direct assignments
CREATE PROCEDURE SP_InsertHistoryRoleAssignments()
BEGIN
  INSERT INTO history_role_assignments (
    dato, user_uuid,
    role_id, role_name, role_it_system_id, role_it_system_name, role_role_group, role_role_group_id,
    assigned_through_type, assigned_through_uuid, assigned_through_name,
    assigned_by_user_id, assigned_by_name, assigned_when, postponed_constraints, ou_uuid, notify_by_email_if_manual_system)
  SELECT CURRENT_TIMESTAMP, u.uuid,
    ur.id, ur.name, it.id, it.name, NULL, NULL,
    'DIRECT', NULL, NULL,
    urm.assigned_by_user_id, urm.assigned_by_name, urm.assigned_timestamp, sub_constraints.combined_constraints, urm.ou_uuid, urm.notify_by_email_if_manual_system
  FROM user_roles_mapping urm
  JOIN users u ON u.uuid = urm.user_uuid
  JOIN user_roles ur ON ur.id = urm.role_id
  JOIN it_systems it ON it.id = ur.it_system_id
  LEFT JOIN
  (
    SELECT
	  pc.user_user_role_assignment_id, group_concat( concat(ct.name,': ',pc.constraint_value) separator '\n' ) as combined_constraints
    FROM postponed_constraints pc
    INNER JOIN constraint_types ct ON ct.id = pc.constraint_type_id
    GROUP BY pc.user_user_role_assignment_id
  ) as sub_constraints ON sub_constraints.user_user_role_assignment_id = urm.id
  WHERE urm.inactive = 0
    AND u.deleted = 0;

  -- user roles through rolegroups from direct assignments
  INSERT INTO history_role_assignments (
    dato, user_uuid,
    role_id, role_name, role_it_system_id, role_it_system_name, role_role_group, role_role_group_id,
    assigned_through_type, assigned_through_uuid, assigned_through_name,
    assigned_by_user_id, assigned_by_name, assigned_when, ou_uuid)
  SELECT CURRENT_TIMESTAMP, u.uuid,
    ur.id, ur.name, it.id, it.name, rg.name, rg.id,
    'ROLEGROUP', NULL, NULL,
    urg.assigned_by_user_id, urg.assigned_by_name, urg.assigned_timestamp, urg.ou_uuid
  FROM user_rolegroups urg
  JOIN rolegroup rg ON urg.rolegroup_id = rg.id
  JOIN users u ON u.uuid = urg.user_uuid
  JOIN rolegroup_roles rgr ON rgr.rolegroup_id = urg.rolegroup_id
  JOIN user_roles ur ON ur.id = rgr.role_id
  JOIN it_systems it ON it.id = ur.it_system_id
  WHERE urg.inactive = 0
    AND u.deleted = 0;

  -- user roles from position assignments
  INSERT INTO history_role_assignments (
    dato, user_uuid,
    role_id, role_name, role_it_system_id, role_it_system_name, role_role_group, role_role_group_id,
    assigned_through_type, assigned_through_uuid, assigned_through_name,
    assigned_by_user_id, assigned_by_name, assigned_when, ou_uuid)
  SELECT CURRENT_TIMESTAMP, u.uuid,
    ur.id, ur.name, it.id, it.name, NULL, NULL,
    'POSITION', ou.uuid, CONCAT(p.name, ' i ', ou.name),
    pr.assigned_by_user_id, pr.assigned_by_name, pr.assigned_timestamp, p.ou_uuid
  FROM position_roles pr
  JOIN positions p ON p.id = pr.position_id
  JOIN users u ON u.uuid = p.user_uuid
  JOIN ous ou ON ou.uuid = p.ou_uuid
  JOIN user_roles ur ON ur.id = pr.role_id
  JOIN it_systems it ON it.id = ur.it_system_id
  WHERE pr.inactive = 0
    AND u.deleted = 0;

  -- user roles through rolegroup from position assignments
  INSERT INTO history_role_assignments (
    dato, user_uuid,
    role_id, role_name, role_it_system_id, role_it_system_name, role_role_group, role_role_group_id,
    assigned_through_type, assigned_through_uuid, assigned_through_name,
    assigned_by_user_id, assigned_by_name, assigned_when, ou_uuid)
  SELECT CURRENT_TIMESTAMP, u.uuid,
    ur.id, ur.name, it.id, it.name, rg.name, rg.id,
    'POSITION', ou.uuid, CONCAT(p.name, ' i ', ou.name),
    prg.assigned_by_user_id, prg.assigned_by_name, prg.assigned_timestamp, p.ou_uuid
  FROM position_rolegroups prg
  JOIN rolegroup rg ON prg.rolegroup_id = rg.id
  JOIN positions p ON p.id = prg.position_id
  JOIN users u ON u.uuid = p.user_uuid
  JOIN ous ou ON ou.uuid = p.ou_uuid
  JOIN rolegroup_roles rgr ON rgr.rolegroup_id = prg.rolegroup_id
  JOIN user_roles ur ON ur.id = rgr.role_id
  JOIN it_systems it ON it.id = ur.it_system_id
  WHERE prg.inactive = 0
    AND u.deleted = 0;

END $$
DELIMITER ;
