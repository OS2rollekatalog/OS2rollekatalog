-- Stored procedure for populating history_role_assignments table
-- should only be called once per day like this
--
-- CALL SP_InsertHistoryRoleAssignments();
--

DELIMITER $$
DROP PROCEDURE IF EXISTS SP_InsertHistoryRoleAssignmentsOUInheritRecursive $$
DROP PROCEDURE IF EXISTS SP_InsertHistoryRoleAssignmentsOUInherit $$

CREATE PROCEDURE SP_InsertHistoryRoleAssignmentsOUInheritRecursive (IN ou_roles_id int, IN orig_ou_uuid VARCHAR(36), IN orig_ou_name VARCHAR(255), IN ou_roles_ou_uuid VARCHAR(36))
BEGIN    
  DECLARE finished INTEGER DEFAULT 0;
  DECLARE child_ou_uuid VARCHAR(36);
  DECLARE cursorChildren CURSOR FOR
    SELECT uuid FROM ous WHERE parent_uuid = ou_roles_ou_uuid;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = 1;

  INSERT INTO history_role_assignments (
    dato, user_uuid,
    role_id, role_name, role_it_system_id, role_it_system_name,
    assigned_through_type, assigned_through_uuid, assigned_through_name,
    assigned_by_user_id, assigned_by_name, assigned_when)
  SELECT
    CURRENT_TIMESTAMP, u.uuid,
    ur.id, ur.name, it.id, it.name,
    'ORGUNIT', orig_ou_uuid, orig_ou_name,
    our.assigned_by_user_id, our.assigned_by_name, our.assigned_timestamp
  FROM ou_roles our
    JOIN ous ou ON ou.uuid = ou_roles_ou_uuid
    JOIN positions p ON p.ou_uuid = ou.uuid
    JOIN users u ON u.uuid = p.user_uuid
    JOIN user_roles ur ON ur.id = our.role_id
    JOIN it_systems it ON it.id = ur.it_system_id
  WHERE our.id = ou_roles_id;

  OPEN cursorChildren;

  getChild: LOOP
    FETCH cursorChildren INTO child_ou_uuid;
    IF finished = 1 THEN
      LEAVE getChild;
    END IF;

    Call SP_InsertHistoryRoleAssignmentsOUInheritRecursive(ou_roles_id, orig_ou_uuid, orig_ou_name, child_ou_uuid);
  END LOOP getChild;
  
  CLOSE cursorChildren;    
END $$

-- handle ou_roles inherited
CREATE PROCEDURE SP_InsertHistoryRoleAssignmentsOUInherit ()
BEGIN
  DECLARE finished INTEGER DEFAULT 0;
  DECLARE ou_roles_id int;
  DECLARE ou_roles_ou_uuid VARCHAR(36);
  DECLARE ou_name VARCHAR(255);
  DECLARE cursorInherited CURSOR FOR
    SELECT our.id, our.ou_uuid, o.name FROM ou_roles our JOIN ous o ON o.uuid = our.ou_uuid WHERE our.inherit = 1;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = 1;

  SET max_sp_recursion_depth=255;
  OPEN cursorInherited;

  getInherited: LOOP
    FETCH cursorInherited INTO ou_roles_id, ou_roles_ou_uuid, ou_name;
    IF finished = 1 THEN
      LEAVE getInherited;
    END IF;

    Call SP_InsertHistoryRoleAssignmentsOUInheritRecursive(ou_roles_id, ou_roles_ou_uuid, ou_name, ou_roles_ou_uuid);
  END LOOP getInherited;

  CLOSE cursorInherited;    
END $$

DELIMITER ;

-- Stored procedure for handling user roles through role groups inherited through OUs

DELIMITER $$
  DROP PROCEDURE IF EXISTS SP_InsertHistoryRoleAssignmentsOURoleGroupInheritRecursive $$
  DROP PROCEDURE IF EXISTS SP_InsertHistoryRoleAssignmentsOURoleGroupInherit $$

  CREATE PROCEDURE SP_InsertHistoryRoleAssignmentsOURoleGroupInheritRecursive (IN ou_roles_id int, IN orig_ou_uuid VARCHAR(36), IN orig_ou_name VARCHAR(255), IN ou_roles_ou_uuid VARCHAR(36))
  BEGIN    
    DECLARE finished INTEGER DEFAULT 0;
    DECLARE child_ou_uuid VARCHAR(36);
    DECLARE cursorChildren CURSOR FOR
      SELECT uuid FROM ous WHERE parent_uuid = ou_roles_ou_uuid;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = 1;

    INSERT INTO history_role_assignments (
      dato, user_uuid,
      role_id, role_name, role_it_system_id, role_it_system_name, role_role_group,
      assigned_through_type, assigned_through_uuid, assigned_through_name,
      assigned_by_user_id, assigned_by_name, assigned_when)
    SELECT
      CURRENT_TIMESTAMP, u.uuid,
      ur.id, ur.name, it.id, it.name, rg.name,
      'ORGUNIT', orig_ou_uuid, orig_ou_name,
      ourg.assigned_by_user_id, ourg.assigned_by_name, ourg.assigned_timestamp
    FROM ou_rolegroups ourg
      JOIN ous ou ON ou.uuid = ou_roles_ou_uuid
      JOIN positions p ON p.ou_uuid = ou.uuid
      JOIN users u ON u.uuid = p.user_uuid
      JOIN rolegroup rg ON rg.id = ourg.rolegroup_id
      JOIN rolegroup_roles rgr ON rgr.rolegroup_id = ourg.rolegroup_id
      JOIN user_roles ur ON ur.id = rgr.role_id
      JOIN it_systems it ON it.id = ur.it_system_id
    WHERE ourg.id = ou_roles_id;

    OPEN cursorChildren;

    getChild: LOOP
      FETCH cursorChildren INTO child_ou_uuid;
      IF finished = 1 THEN
        LEAVE getChild;
      END IF;

      Call SP_InsertHistoryRoleAssignmentsOURoleGroupInheritRecursive(ou_roles_id, orig_ou_uuid, orig_ou_name, child_ou_uuid);
    END LOOP getChild;
    
    CLOSE cursorChildren;    
END $$

-- handle ou_roles inherited
CREATE PROCEDURE SP_InsertHistoryRoleAssignmentsOURoleGroupInherit ()
BEGIN    
  DECLARE finished INTEGER DEFAULT 0;
  DECLARE ou_roles_id int;
  DECLARE ou_roles_ou_uuid VARCHAR(36);
  DECLARE ou_name VARCHAR(255);
  DECLARE cursorInherited CURSOR FOR
    SELECT ourg.id, ourg.ou_uuid, o.name FROM ou_rolegroups ourg JOIN ous o ON o.uuid = ourg.ou_uuid WHERE ourg.inherit = 1;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = 1;
 
  SET max_sp_recursion_depth=255;
  OPEN cursorInherited;

  getInherited: LOOP
    FETCH cursorInherited INTO ou_roles_id,ou_roles_ou_uuid, ou_name;
    IF finished = 1 THEN
      LEAVE getInherited;
    END IF;
    
    Call SP_InsertHistoryRoleAssignmentsOURoleGroupInheritRecursive(ou_roles_id, ou_roles_ou_uuid, ou_name, ou_roles_ou_uuid);
  END LOOP getInherited;
  
  CLOSE cursorInherited;    
END $$

DELIMITER ;


-- The primary stored procedure, that does everything - should be called once per day

DELIMITER $$
DROP PROCEDURE IF EXISTS SP_InsertHistoryRoleAssignments $$

-- user roles from direct assignments
CREATE PROCEDURE SP_InsertHistoryRoleAssignments()
BEGIN
  INSERT INTO history_role_assignments (
    dato, user_uuid,
    role_id, role_name, role_it_system_id, role_it_system_name, role_role_group,
    assigned_through_type, assigned_through_uuid, assigned_through_name,
    assigned_by_user_id, assigned_by_name, assigned_when)
  SELECT CURRENT_TIMESTAMP, u.uuid,
    ur.id, ur.name, it.id, it.name, NULL,
    'DIRECT', NULL, NULL,
    urm.assigned_by_user_id, urm.assigned_by_name, urm.assigned_timestamp
  FROM user_roles_mapping urm
  JOIN users u ON u.uuid = urm.user_uuid
  JOIN user_roles ur ON ur.id = urm.role_id
  JOIN it_systems it ON it.id = ur.it_system_id;

  -- user roles through rolegroups from direct assignments
  INSERT INTO history_role_assignments (
    dato, user_uuid,
    role_id, role_name, role_it_system_id, role_it_system_name, role_role_group,
    assigned_through_type, assigned_through_uuid, assigned_through_name,
    assigned_by_user_id, assigned_by_name, assigned_when)
  SELECT CURRENT_TIMESTAMP, u.uuid,
    ur.id, ur.name, it.id, it.name, rg.name,
    'DIRECT', NULL, NULL,
    urg.assigned_by_user_id, urg.assigned_by_name, urg.assigned_timestamp
  FROM user_rolegroups urg
  JOIN rolegroup rg ON urg.rolegroup_id = rg.id
  JOIN users u ON u.uuid = urg.user_uuid
  JOIN rolegroup_roles rgr ON rgr.rolegroup_id = urg.rolegroup_id
  JOIN user_roles ur ON ur.id = rgr.role_id
  JOIN it_systems it ON it.id = ur.it_system_id;

  -- user roles from position assignments
  INSERT INTO history_role_assignments (
    dato, user_uuid,
    role_id, role_name, role_it_system_id, role_it_system_name, role_role_group,
    assigned_through_type, assigned_through_uuid, assigned_through_name,
    assigned_by_user_id, assigned_by_name, assigned_when)
  SELECT CURRENT_TIMESTAMP, u.uuid,
    ur.id, ur.name, it.id, it.name, NULL,
    'POSITION', ou.uuid, CONCAT(p.name, ' i ', ou.name),
    pr.assigned_by_user_id, pr.assigned_by_name, pr.assigned_timestamp
  FROM position_roles pr
  JOIN positions p ON p.id = pr.position_id
  JOIN users u ON u.uuid = p.user_uuid
  JOIN ous ou ON ou.uuid = p.ou_uuid
  JOIN user_roles ur ON ur.id = pr.role_id
  JOIN it_systems it ON it.id = ur.it_system_id;

  -- user roles through rolegroup from position assignments
  INSERT INTO history_role_assignments (
    dato, user_uuid,
    role_id, role_name, role_it_system_id, role_it_system_name, role_role_group,
    assigned_through_type, assigned_through_uuid, assigned_through_name,
    assigned_by_user_id, assigned_by_name, assigned_when)
  SELECT CURRENT_TIMESTAMP, u.uuid,
    ur.id, ur.name, it.id, it.name, rg.name,
    'POSITION', ou.uuid, CONCAT(p.name, ' i ', ou.name),
    prg.assigned_by_user_id, prg.assigned_by_name, prg.assigned_timestamp
  FROM position_rolegroups prg
  JOIN rolegroup rg ON prg.rolegroup_id = rg.id
  JOIN positions p ON p.id = prg.position_id
  JOIN users u ON u.uuid = p.user_uuid
  JOIN ous ou ON ou.uuid = p.ou_uuid
  JOIN rolegroup_roles rgr ON rgr.rolegroup_id = prg.rolegroup_id
  JOIN user_roles ur ON ur.id = rgr.role_id
  JOIN it_systems it ON it.id = ur.it_system_id;


  -- user roles from orgunits (not inherited)
  INSERT INTO history_role_assignments (
    dato, user_uuid,
    role_id, role_name, role_it_system_id, role_it_system_name, role_role_group,
    assigned_through_type, assigned_through_uuid, assigned_through_name,
    assigned_by_user_id, assigned_by_name, assigned_when)
  SELECT CURRENT_TIMESTAMP, u.uuid,
    ur.id, ur.name, it.id, it.name, NULL,
    'ORGUNIT', ou.uuid, ou.name,
    our.assigned_by_user_id, our.assigned_by_name, our.assigned_timestamp
  FROM ou_roles our
  JOIN ous ou ON ou.uuid = our.ou_uuid
  JOIN positions p ON p.ou_uuid = our.ou_uuid
  JOIN users u ON u.uuid = p.user_uuid
  JOIN user_roles ur ON ur.id = our.role_id
  JOIN it_systems it ON it.id = ur.it_system_id
  WHERE our.inherit = 0;


  -- user roles through rolegroups from orgunits (not inherited)
  INSERT INTO history_role_assignments (
    dato, user_uuid,
    role_id, role_name, role_it_system_id, role_it_system_name, role_role_group,
    assigned_through_type, assigned_through_uuid, assigned_through_name,
    assigned_by_user_id, assigned_by_name, assigned_when)
  SELECT CURRENT_TIMESTAMP, u.uuid,
    ur.id, ur.name, it.id, it.name, rg.name,
    'ORGUNIT', ou.uuid, ou.name,
    ourg.assigned_by_user_id, ourg.assigned_by_name, ourg.assigned_timestamp
  FROM ou_rolegroups ourg
  JOIN rolegroup rg ON ourg.rolegroup_id = rg.id
  JOIN ous ou ON ou.uuid = ourg.ou_uuid
  JOIN positions p ON p.ou_uuid = ourg.ou_uuid
  JOIN users u ON u.uuid = p.user_uuid
  JOIN rolegroup_roles rgr ON rgr.rolegroup_id = ourg.rolegroup_id
  JOIN user_roles ur ON ur.id = rgr.role_id
  JOIN it_systems it ON it.id = ur.it_system_id
  WHERE ourg.inherit = 0;


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
  JOIN it_systems it ON it.id = ur.it_system_id;

  
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
  JOIN it_systems it ON it.id = ur.it_system_id;
  
  -- user roles from orgunits (inherited)
  CALL SP_InsertHistoryRoleAssignmentsOUInherit();

  -- user roles through rolegroups from orgunits (inherited)
  CALL SP_InsertHistoryRoleAssignmentsOURoleGroupInherit();

END $$
DELIMITER ;
