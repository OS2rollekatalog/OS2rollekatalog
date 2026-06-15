-- Stored procedure for populating history_ou_role_assignments table
-- should only be called once per day like this
--
-- CALL SP_InsertHistoryOURoleAssignments();
--

DELIMITER $$
DROP PROCEDURE IF EXISTS SP_InsertHistoryOURoleAssignmentsOUInheritRecursive $$
DROP PROCEDURE IF EXISTS SP_InsertHistoryOURoleAssignmentsOUInherit $$

CREATE PROCEDURE SP_InsertHistoryOURoleAssignmentsOUInheritRecursive (IN ou_roles_id int, IN orig_ou_uuid VARCHAR(36), IN orig_ou_name VARCHAR(255), IN ou_roles_ou_uuid VARCHAR(36))
BEGIN
  DECLARE finished INTEGER DEFAULT 0;
  DECLARE child_ou_uuid varchar(36);
  DECLARE new_assignment_id BIGINT DEFAULT NULL;
  DECLARE inserted_rows INT DEFAULT 0;
  DECLARE cursorChildren CURSOR FOR
    SELECT uuid FROM ous WHERE parent_uuid = ou_roles_ou_uuid AND active = 1;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = 1;

  -- Insert the main assignment record
  INSERT INTO history_ou_role_assignments (
    dato, ou_uuid,
    role_id, role_name, role_it_system_id, role_it_system_name,
    assigned_through_type, assigned_through_uuid, assigned_through_name,
    assigned_by_user_id, assigned_by_name, assigned_when, `inherit`, start_date, stop_date, manager, substitutes)
  SELECT
    CURRENT_TIMESTAMP, ou.uuid,
    ur.id, ur.name, it.id, it.name,
    'ORGUNIT', orig_ou_uuid, orig_ou_name,
    our.assigned_by_user_id, our.assigned_by_name, our.assigned_timestamp,
    orig_ou_uuid = ou_roles_ou_uuid, our.start_date, our.stop_date,
    our.manager, our.substitutes
  FROM ou_roles our
    JOIN ous ou ON ou.uuid = ou_roles_ou_uuid
    JOIN user_roles ur ON ur.id = our.role_id
    JOIN it_systems it ON it.id = ur.it_system_id
  WHERE our.id = ou_roles_id;

  -- Capture the id and row count of the assignment just inserted. Both functions still
  -- refer to the INSERT above until this SET completes. Subsequent exclusion inserts have
  -- their own AUTO_INCREMENT, so LAST_INSERT_ID() would otherwise be overwritten by the
  -- first exclusion row's id and break the FK on later exclusion inserts. The row count
  -- guard avoids attaching exclusions to a stale id if the main insert matched no rows.
  SET new_assignment_id = LAST_INSERT_ID(), inserted_rows = ROW_COUNT();

  IF inserted_rows > 0 THEN
    -- Insert exclusions for excepted users
    INSERT INTO history_ou_role_assignment_exclusions (assignment_id, exclusion_type, user_uuids)
    SELECT
      new_assignment_id, 'excepted_users', GROUP_CONCAT(oureu.user_uuid)
    FROM ou_roles our
      JOIN ou_roles_excepted_users oureu ON oureu.ou_roles_id = our.id
    WHERE our.id = ou_roles_id AND our.contains_excepted_users = 1
    HAVING GROUP_CONCAT(oureu.user_uuid) IS NOT NULL;

    -- Insert exclusions for positive titles
    INSERT INTO history_ou_role_assignment_exclusions (assignment_id, exclusion_type, title_uuids)
    SELECT
      new_assignment_id, 'titles', GROUP_CONCAT(ourt.title_uuid)
    FROM ou_roles our
      JOIN ou_roles_titles ourt ON ourt.ou_roles_id = our.id
    WHERE our.id = ou_roles_id AND our.contains_titles = 1
    HAVING GROUP_CONCAT(ourt.title_uuid) IS NOT NULL;

    -- Insert exclusions for negative titles
    INSERT INTO history_ou_role_assignment_exclusions (assignment_id, exclusion_type, title_uuids)
    SELECT
      new_assignment_id, 'negative_titles', GROUP_CONCAT(ourt.title_uuid)
    FROM ou_roles our
      JOIN ou_roles_titles ourt ON ourt.ou_roles_id = our.id
    WHERE our.id = ou_roles_id AND our.contains_titles = 2
    HAVING GROUP_CONCAT(ourt.title_uuid) IS NOT NULL;

    -- Insert exclusions for functions
    INSERT INTO history_ou_role_assignment_exclusions (assignment_id, exclusion_type, function_uuids)
    SELECT
      new_assignment_id, 'functions', GROUP_CONCAT(ourf.function_uuid)
    FROM ou_roles our
      JOIN ou_roles_functions ourf ON ourf.ou_roles_id = our.id
    WHERE our.id = ou_roles_id AND our.contains_functions = 1
    HAVING GROUP_CONCAT(ourf.function_uuid) IS NOT NULL;
  END IF;

  OPEN cursorChildren;

  getChild: LOOP
    FETCH cursorChildren INTO child_ou_uuid;
    IF finished = 1 THEN
      LEAVE getChild;
    END IF;

    Call SP_InsertHistoryOURoleAssignmentsOUInheritRecursive(ou_roles_id, orig_ou_uuid, orig_ou_name, child_ou_uuid);
  END LOOP getChild;
  
  CLOSE cursorChildren;    
END $$

-- handle ou_roles inherited
CREATE PROCEDURE SP_InsertHistoryOURoleAssignmentsOUInherit ()
BEGIN
  DECLARE finished INTEGER DEFAULT 0;
  DECLARE ou_roles_id int;
  DECLARE ou_roles_ou_uuid VARCHAR(36);
  DECLARE ou_name VARCHAR(255);
  DECLARE cursorInherited CURSOR FOR
    SELECT our.id, our.ou_uuid, o.name FROM ou_roles our JOIN ous o ON o.uuid = our.ou_uuid WHERE our.inherit = 1 AND o.active = 1;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = 1;

  SET max_sp_recursion_depth=255;
  OPEN cursorInherited;

  getInherited: LOOP
    FETCH cursorInherited INTO ou_roles_id, ou_roles_ou_uuid, ou_name;
    IF finished = 1 THEN
      LEAVE getInherited;
    END IF;

    Call SP_InsertHistoryOURoleAssignmentsOUInheritRecursive(ou_roles_id, ou_roles_ou_uuid, ou_name, ou_roles_ou_uuid);
  END LOOP getInherited;

  CLOSE cursorInherited;
END $$

DELIMITER ;

-- Stored procedure for handling user roles through role groups inherited through OUs

DELIMITER $$
  DROP PROCEDURE IF EXISTS SP_InsertHistoryOURoleAssignmentsOURoleGroupInheritRecursive $$
  DROP PROCEDURE IF EXISTS SP_InsertHistoryOURoleAssignmentsOURoleGroupInherit $$

  CREATE PROCEDURE SP_InsertHistoryOURoleAssignmentsOURoleGroupInheritRecursive (IN ou_roles_id int, IN orig_ou_uuid VARCHAR(36), IN orig_ou_name VARCHAR(255), IN ou_roles_ou_uuid VARCHAR(36))
  BEGIN    
    DECLARE finished INTEGER DEFAULT 0;
    DECLARE child_ou_uuid VARCHAR(36);
    DECLARE new_assignment_id BIGINT DEFAULT NULL;
    DECLARE inserted_rows INT DEFAULT 0;
    DECLARE cursorChildren CURSOR FOR
      SELECT uuid FROM ous WHERE parent_uuid = ou_roles_ou_uuid AND active = 1;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = 1;

    -- Insert the main assignment record
    INSERT INTO history_ou_role_assignments (
      dato, ou_uuid,
      role_id, role_name, role_it_system_id, role_it_system_name, role_role_group, role_role_group_id,
      assigned_through_type, assigned_through_uuid, assigned_through_name,
      assigned_by_user_id, assigned_by_name, assigned_when, `inherit`, start_date, stop_date, manager, substitutes)
    SELECT
      CURRENT_TIMESTAMP, ou.uuid,
      ur.id, ur.name, it.id, it.name, rg.name, rg.id,
      'ORGUNIT', orig_ou_uuid, orig_ou_name,
      ourg.assigned_by_user_id, ourg.assigned_by_name, ourg.assigned_timestamp, 0, ourg.start_date,
      ourg.stop_date, ourg.manager, ourg.substitutes
    FROM ou_rolegroups ourg
      JOIN ous ou ON ou.uuid = ou_roles_ou_uuid
      JOIN rolegroup rg ON rg.id = ourg.rolegroup_id
      JOIN rolegroup_roles rgr ON rgr.rolegroup_id = ourg.rolegroup_id
      JOIN user_roles ur ON ur.id = rgr.role_id
      JOIN it_systems it ON it.id = ur.it_system_id
    WHERE ourg.id = ou_roles_id;

    -- Capture the id and row count of the (first) assignment just inserted. Both functions
    -- still refer to the INSERT above until this SET completes. Subsequent exclusion inserts
    -- have their own AUTO_INCREMENT, so LAST_INSERT_ID() would otherwise be overwritten by the
    -- first exclusion row's id and break the FK on later exclusion inserts. The row count
    -- guard avoids attaching exclusions to a stale id if the main insert matched no rows.
    SET new_assignment_id = LAST_INSERT_ID(), inserted_rows = ROW_COUNT();

    IF inserted_rows > 0 THEN
      -- Insert exclusions for excepted users
      INSERT INTO history_ou_role_assignment_exclusions (assignment_id, exclusion_type, user_uuids)
      SELECT
        new_assignment_id, 'excepted_users', GROUP_CONCAT(ourgeu.user_uuid)
      FROM ou_rolegroups ourg
        JOIN ou_rolegroups_excepted_users ourgeu ON ourgeu.ou_rolegroups_id = ourg.id
      WHERE ourg.id = ou_roles_id AND ourg.contains_excepted_users = 1
      HAVING GROUP_CONCAT(ourgeu.user_uuid) IS NOT NULL;

      -- Insert exclusions for positive titles
      INSERT INTO history_ou_role_assignment_exclusions (assignment_id, exclusion_type, title_uuids)
      SELECT
        new_assignment_id, 'titles', GROUP_CONCAT(ourgt.title_uuid)
      FROM ou_rolegroups ourg
        JOIN ou_rolegroups_titles ourgt ON ourgt.ou_rolegroups_id = ourg.id
      WHERE ourg.id = ou_roles_id AND ourg.contains_titles = 1
      HAVING GROUP_CONCAT(ourgt.title_uuid) IS NOT NULL;

      -- Insert exclusions for negative titles
      INSERT INTO history_ou_role_assignment_exclusions (assignment_id, exclusion_type, title_uuids)
      SELECT
        new_assignment_id, 'negative_titles', GROUP_CONCAT(ourgt.title_uuid)
      FROM ou_rolegroups ourg
        JOIN ou_rolegroups_titles ourgt ON ourgt.ou_rolegroups_id = ourg.id
      WHERE ourg.id = ou_roles_id AND ourg.contains_titles = 2
      HAVING GROUP_CONCAT(ourgt.title_uuid) IS NOT NULL;

      -- Insert exclusions for functions
      INSERT INTO history_ou_role_assignment_exclusions (assignment_id, exclusion_type, function_uuids)
      SELECT
        new_assignment_id, 'functions', GROUP_CONCAT(ourgf.function_uuid)
      FROM ou_rolegroups ourg
        JOIN ou_rolegroups_functions ourgf ON ourgf.ou_rolegroups_id = ourg.id
      WHERE ourg.id = ou_roles_id AND ourg.contains_functions = 1
      HAVING GROUP_CONCAT(ourgf.function_uuid) IS NOT NULL;
    END IF;

    OPEN cursorChildren;

    getChild: LOOP
      FETCH cursorChildren INTO child_ou_uuid;
      IF finished = 1 THEN
        LEAVE getChild;
      END IF;

      Call SP_InsertHistoryOURoleAssignmentsOURoleGroupInheritRecursive(ou_roles_id, orig_ou_uuid, orig_ou_name, child_ou_uuid);
    END LOOP getChild;
    
    CLOSE cursorChildren;    
END $$

-- handle ou_roles inherited
CREATE PROCEDURE SP_InsertHistoryOURoleAssignmentsOURoleGroupInherit ()
BEGIN    
  DECLARE finished INTEGER DEFAULT 0;
  DECLARE ou_roles_id int;
  DECLARE ou_roles_ou_uuid VARCHAR(36);
  DECLARE ou_name VARCHAR(255);
  DECLARE cursorInherited CURSOR FOR
    SELECT ourg.id, ourg.ou_uuid, o.name FROM ou_rolegroups ourg JOIN ous o ON o.uuid = ourg.ou_uuid WHERE ourg.inherit = 1 AND o.active = 1;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = 1;
 
  SET max_sp_recursion_depth=255;
  OPEN cursorInherited;

  getInherited: LOOP
    FETCH cursorInherited INTO ou_roles_id, ou_roles_ou_uuid, ou_name;
    IF finished = 1 THEN
      LEAVE getInherited;
    END IF;

    Call SP_InsertHistoryOURoleAssignmentsOURoleGroupInheritRecursive(ou_roles_id, ou_roles_ou_uuid, ou_name, ou_roles_ou_uuid);
  END LOOP getInherited;
  
  CLOSE cursorInherited;    
END $$

DELIMITER ;


-- The primary stored procedure, that does everything - should be called once per day

DELIMITER $$
DROP PROCEDURE IF EXISTS SP_InsertHistoryOURoleAssignments $$

-- user roles from direct assignments
CREATE PROCEDURE SP_InsertHistoryOURoleAssignments()
BEGIN
  -- Direct role assignments 
  INSERT INTO history_ou_role_assignments (
    dato, ou_uuid,
    role_id, role_name, role_it_system_id, role_it_system_name, role_role_group, role_role_group_id,
    assigned_through_type, assigned_through_uuid, assigned_through_name,
    assigned_by_user_id, assigned_by_name, assigned_when, inherit, start_date, stop_date,
    manager, substitutes)
  SELECT CURRENT_TIMESTAMP, o.uuid,
    ur.id, ur.name, it.id, it.name, NULL, NULL,
    'DIRECT', NULL, NULL,
    our.assigned_by_user_id, our.assigned_by_name, our.assigned_timestamp, 0, our.start_date,
    our.stop_date, our.manager, our.substitutes
  FROM ou_roles our
  JOIN ous o ON o.uuid = our.ou_uuid
  JOIN user_roles ur ON ur.id = our.role_id
  JOIN it_systems it ON it.id = ur.it_system_id
  WHERE our.inherit = 0 AND o.active = 1;

  -- Insert exclusions for direct role assignments
  INSERT INTO history_ou_role_assignment_exclusions (assignment_id, exclusion_type, user_uuids)
  SELECT 
    h.id, 'excepted_users', GROUP_CONCAT(oureu.user_uuid)
  FROM history_ou_role_assignments h
  JOIN ou_roles our ON our.ou_uuid = h.ou_uuid AND our.role_id = h.role_id 
    AND our.assigned_by_user_id = h.assigned_by_user_id AND our.assigned_timestamp = h.assigned_when
  JOIN ou_roles_excepted_users oureu ON oureu.ou_roles_id = our.id
  WHERE h.dato = CURRENT_DATE() AND our.contains_excepted_users = 1 AND h.role_role_group_id IS NULL
  GROUP BY h.id
  HAVING GROUP_CONCAT(oureu.user_uuid) IS NOT NULL;

  INSERT INTO history_ou_role_assignment_exclusions (assignment_id, exclusion_type, title_uuids)
  SELECT 
    h.id, 'titles', GROUP_CONCAT(ourt.title_uuid)
  FROM history_ou_role_assignments h
  JOIN ou_roles our ON our.ou_uuid = h.ou_uuid AND our.role_id = h.role_id 
    AND our.assigned_by_user_id = h.assigned_by_user_id AND our.assigned_timestamp = h.assigned_when
  JOIN ou_roles_titles ourt ON ourt.ou_roles_id = our.id
  WHERE h.dato = CURRENT_DATE() AND our.contains_titles = 1 AND h.role_role_group_id IS NULL
  GROUP BY h.id
  HAVING GROUP_CONCAT(ourt.title_uuid) IS NOT NULL;

  INSERT INTO history_ou_role_assignment_exclusions (assignment_id, exclusion_type, title_uuids)
  SELECT 
    h.id, 'negative_titles', GROUP_CONCAT(ourt.title_uuid)
  FROM history_ou_role_assignments h
  JOIN ou_roles our ON our.ou_uuid = h.ou_uuid AND our.role_id = h.role_id 
    AND our.assigned_by_user_id = h.assigned_by_user_id AND our.assigned_timestamp = h.assigned_when
  JOIN ou_roles_titles ourt ON ourt.ou_roles_id = our.id
  WHERE h.dato = CURRENT_DATE() AND our.contains_titles = 2 AND h.role_role_group_id IS NULL
  GROUP BY h.id
  HAVING GROUP_CONCAT(ourt.title_uuid) IS NOT NULL;

  INSERT INTO history_ou_role_assignment_exclusions (assignment_id, exclusion_type, function_uuids)
  SELECT
    h.id, 'functions', GROUP_CONCAT(ourf.function_uuid)
  FROM history_ou_role_assignments h
  JOIN ou_roles our ON our.ou_uuid = h.ou_uuid AND our.role_id = h.role_id
    AND our.assigned_by_user_id = h.assigned_by_user_id AND our.assigned_timestamp = h.assigned_when
  JOIN ou_roles_functions ourf ON ourf.ou_roles_id = our.id
  WHERE h.dato = CURRENT_DATE() AND our.contains_functions = 1 AND h.role_role_group_id IS NULL
  GROUP BY h.id
  HAVING GROUP_CONCAT(ourf.function_uuid) IS NOT NULL;

  -- Direct role assignments through role groups
  INSERT INTO history_ou_role_assignments (
    dato, ou_uuid,
    role_id, role_name, role_it_system_id, role_it_system_name, role_role_group, role_role_group_id,
    assigned_through_type, assigned_through_uuid, assigned_through_name,
    assigned_by_user_id, assigned_by_name, assigned_when, inherit, start_date, stop_date,
    manager, substitutes)
  SELECT CURRENT_TIMESTAMP, o.uuid,
    ur.id, ur.name, it.id, it.name, rg.name, rg.id,
    'DIRECT', NULL, NULL,
    ourg.assigned_by_user_id, ourg.assigned_by_name, ourg.assigned_timestamp, 0, ourg.start_date,
    ourg.stop_date, ourg.manager, ourg.substitutes
  FROM ou_rolegroups ourg
  JOIN ous o ON o.uuid = ourg.ou_uuid
  JOIN rolegroup rg ON ourg.rolegroup_id = rg.id
  JOIN rolegroup_roles rgr ON rgr.rolegroup_id = ourg.rolegroup_id
  JOIN user_roles ur ON ur.id = rgr.role_id
  JOIN it_systems it ON it.id = ur.it_system_id
  WHERE ourg.inherit = 0 AND o.active = 1;

  -- Insert exclusions for role group assignments
  INSERT INTO history_ou_role_assignment_exclusions (assignment_id, exclusion_type, user_uuids)
  SELECT 
    h.id, 'excepted_users', GROUP_CONCAT(ourgeu.user_uuid)
  FROM history_ou_role_assignments h
  JOIN ou_rolegroups ourg ON ourg.ou_uuid = h.ou_uuid AND ourg.rolegroup_id = h.role_role_group_id
    AND ourg.assigned_by_user_id = h.assigned_by_user_id AND ourg.assigned_timestamp = h.assigned_when
  JOIN ou_rolegroups_excepted_users ourgeu ON ourgeu.ou_rolegroups_id = ourg.id
  WHERE h.dato = CURRENT_DATE() AND ourg.contains_excepted_users = 1 AND h.role_role_group_id IS NOT NULL
  GROUP BY h.id
  HAVING GROUP_CONCAT(ourgeu.user_uuid) IS NOT NULL;

  INSERT INTO history_ou_role_assignment_exclusions (assignment_id, exclusion_type, title_uuids)
  SELECT 
    h.id, 'titles', GROUP_CONCAT(ourgt.title_uuid)
  FROM history_ou_role_assignments h
  JOIN ou_rolegroups ourg ON ourg.ou_uuid = h.ou_uuid AND ourg.rolegroup_id = h.role_role_group_id
    AND ourg.assigned_by_user_id = h.assigned_by_user_id AND ourg.assigned_timestamp = h.assigned_when
  JOIN ou_rolegroups_titles ourgt ON ourgt.ou_rolegroups_id = ourg.id
  WHERE h.dato = CURRENT_DATE() AND ourg.contains_titles = 1 AND h.role_role_group_id IS NOT NULL
  GROUP BY h.id
  HAVING GROUP_CONCAT(ourgt.title_uuid) IS NOT NULL;

  INSERT INTO history_ou_role_assignment_exclusions (assignment_id, exclusion_type, title_uuids)
  SELECT 
    h.id, 'negative_titles', GROUP_CONCAT(ourgt.title_uuid)
  FROM history_ou_role_assignments h
  JOIN ou_rolegroups ourg ON ourg.ou_uuid = h.ou_uuid AND ourg.rolegroup_id = h.role_role_group_id
    AND ourg.assigned_by_user_id = h.assigned_by_user_id AND ourg.assigned_timestamp = h.assigned_when
  JOIN ou_rolegroups_titles ourgt ON ourgt.ou_rolegroups_id = ourg.id
  WHERE h.dato = CURRENT_DATE() AND ourg.contains_titles = 2 AND h.role_role_group_id IS NOT NULL
  GROUP BY h.id
  HAVING GROUP_CONCAT(ourgt.title_uuid) IS NOT NULL;

  INSERT INTO history_ou_role_assignment_exclusions (assignment_id, exclusion_type, function_uuids)
  SELECT
    h.id, 'functions', GROUP_CONCAT(ourgf.function_uuid)
  FROM history_ou_role_assignments h
  JOIN ou_rolegroups ourg ON ourg.ou_uuid = h.ou_uuid AND ourg.rolegroup_id = h.role_role_group_id
    AND ourg.assigned_by_user_id = h.assigned_by_user_id AND ourg.assigned_timestamp = h.assigned_when
  JOIN ou_rolegroups_functions ourgf ON ourgf.ou_rolegroups_id = ourg.id
  WHERE h.dato = CURRENT_DATE() AND ourg.contains_functions = 1 AND h.role_role_group_id IS NOT NULL
  GROUP BY h.id
  HAVING GROUP_CONCAT(ourgf.function_uuid) IS NOT NULL;

  -- Inherited role assignments from orgunits
  CALL SP_InsertHistoryOURoleAssignmentsOUInherit();

  -- Inherited role assignments through rolegroups from orgunits
  CALL SP_InsertHistoryOURoleAssignmentsOURoleGroupInherit();

END $$
DELIMITER ;
