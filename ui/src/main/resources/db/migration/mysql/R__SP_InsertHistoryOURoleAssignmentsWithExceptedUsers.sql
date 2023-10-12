-- Stored procedure for populating history_ou_role_assignments table
-- should only be called once per day like this
--
-- CALL SP_InsertHistoryOURoleAssignmentsWithExceptions();
--

DELIMITER $$
DROP PROCEDURE IF EXISTS SP_InsertHistoryOURoleAssignmentsWithExceptions $$

-- user roles from ou assignments
CREATE PROCEDURE SP_InsertHistoryOURoleAssignmentsWithExceptions()
BEGIN
  INSERT INTO history_role_assignment_excepted_users (
    dato, ou_uuid, user_uuids,
    role_id, role_name, role_it_system_id, role_it_system_name, role_role_group, role_role_group_id,
    assigned_by_user_id, assigned_by_name, assigned_when)
  SELECT
    CURRENT_TIMESTAMP, o.uuid, COALESCE(GROUP_CONCAT(oureu.user_uuid), ''),
    ur.id, ur.name, it.id, it.name, NULL, NULL,
    our.assigned_by_user_id, our.assigned_by_name, our.assigned_timestamp
  FROM ou_roles our
  JOIN ous o ON o.uuid = our.ou_uuid
  JOIN user_roles ur ON ur.id = our.role_id
  JOIN it_systems it ON it.id = ur.it_system_id
  LEFT JOIN ou_roles_excepted_users oureu ON oureu.ou_roles_id = our.id
  WHERE our.inherit = 0 AND o.active = 1 AND our.inactive = 0 AND our.contains_excepted_users = 1
  GROUP BY our.id;

  -- user roles through rolegroups from direct assignments
  INSERT INTO history_role_assignment_excepted_users (
    dato, ou_uuid, user_uuids,
    role_id, role_name, role_it_system_id, role_it_system_name, role_role_group, role_role_group_id,
    assigned_by_user_id, assigned_by_name, assigned_when)
  SELECT
    CURRENT_TIMESTAMP, o.uuid, COALESCE(GROUP_CONCAT(ourgeu.user_uuid), ''),
    ur.id, ur.name, it.id, it.name, rg.name, rg.id,
    ourg.assigned_by_user_id, ourg.assigned_by_name, ourg.assigned_timestamp
  FROM ou_rolegroups ourg
  JOIN ous o ON o.uuid = ourg.ou_uuid
  JOIN rolegroup rg ON ourg.rolegroup_id = rg.id
  JOIN rolegroup_roles rgr ON rgr.rolegroup_id = ourg.rolegroup_id
  JOIN user_roles ur ON ur.id = rgr.role_id
  JOIN it_systems it ON it.id = ur.it_system_id
  LEFT JOIN ou_rolegroups_excepted_users ourgeu ON ourgeu.ou_rolegroups_id = ourg.id
  WHERE ourg.inherit = 0 AND o.active = 1 AND ourg.inactive = 0 AND ourg.contains_excepted_users = 1
  GROUP BY ourg.id, ur.id;

END $$
DELIMITER ;
