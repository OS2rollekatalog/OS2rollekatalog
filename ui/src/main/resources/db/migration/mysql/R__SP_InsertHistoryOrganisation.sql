-- Stored procedure for populating history_kle_assignments table
-- should only be called once per day like this
--
-- CALL SP_InsertHistoryOrganisation();
--

DELIMITER $$
DROP PROCEDURE IF EXISTS SP_InsertHistoryOrganisation $$

CREATE PROCEDURE SP_InsertHistoryOrganisation() 
BEGIN

  INSERT INTO history_ous (dato, ou_uuid, ou_name, ou_parent_uuid, ou_manager_uuid, ou_substitute_uuids)
    SELECT
      CURRENT_TIMESTAMP,
      o.uuid,
      o.name,
      o.parent_uuid,
      o.manager,
      GROUP_CONCAT(DISTINCT ums.substitute_uuid ORDER BY ums.substitute_uuid SEPARATOR ',')
    FROM ous o
    LEFT JOIN users_manager_substitute ums ON ums.manager_uuid = o.manager AND ums.ou_uuid = o.uuid
    WHERE o.active = 1
    GROUP BY o.uuid, o.name, o.parent_uuid, o.manager;

  -- Users with positions in OUs (includes functions from user_ou_function for this user+ou combination)
  INSERT INTO history_ous_users (history_ous_id, user_uuid, title_uuid, do_not_inherit, function_uuids, has_position)
  SELECT ho.id,
         u.uuid,
         p.title_uuid,
         p.do_not_inherit,
         GROUP_CONCAT(DISTINCT uof.function_uuid ORDER BY uof.function_uuid SEPARATOR ','),
         1  -- TRUE: User has a position in this OU
  FROM history_ous ho
  JOIN positions p ON p.ou_uuid = ho.ou_uuid
  JOIN users u ON u.uuid = p.user_uuid
  LEFT JOIN user_ou_function uof ON uof.ou_uuid = p.ou_uuid AND uof.user_uuid = p.user_uuid
  WHERE ho.dato = CAST(CURRENT_TIMESTAMP AS DATE)
    AND u.deleted = 0
  GROUP BY p.id;

  -- Users with standalone function assignments (without positions in the OU)
  INSERT INTO history_ous_users (history_ous_id, user_uuid, title_uuid, do_not_inherit, function_uuids, has_position)
  SELECT ho.id,
         u.uuid,
         NULL,
         0,
         GROUP_CONCAT(DISTINCT uof.function_uuid ORDER BY uof.function_uuid SEPARATOR ','),
         0  -- FALSE: User only has function assignment, no position
  FROM history_ous ho
  JOIN user_ou_function uof ON uof.ou_uuid = ho.ou_uuid
  JOIN users u ON u.uuid = uof.user_uuid
  WHERE ho.dato = CAST(CURRENT_TIMESTAMP AS DATE)
    AND u.deleted = 0
    -- Only users without positions in this OU
    AND NOT EXISTS (
      SELECT 1 FROM positions p
      WHERE p.user_uuid = u.uuid AND p.ou_uuid = ho.ou_uuid
    )
  GROUP BY ho.id, u.uuid;

  -- Users (without functions - those are stored in history_ous_users)
  INSERT INTO history_users (
    dato, user_uuid, user_ext_uuid, user_name, user_user_id,
    domain_id, user_active)
  SELECT
    CURRENT_TIMESTAMP,
    u.uuid,
    u.ext_uuid,
    u.name,
    u.user_id,
    u.domain_id,
    NOT u.disabled
  FROM users u
  WHERE u.deleted = 0;

  INSERT INTO history_managers (dato, user_uuid, user_name)
  SELECT DISTINCT CURRENT_TIMESTAMP, o.manager, u.name
  FROM ous o
  LEFT JOIN users u ON u.uuid = o.manager
  WHERE o.manager IS NOT NULL AND o.active = 1;

  INSERT INTO history_titles (dato, title_uuid, title_name)
  SELECT CURRENT_TIMESTAMP, t.uuid, t.name
  FROM titles t
  WHERE t.active = 1;

  INSERT INTO history_functions (dato, function_uuid, function_name)
  SELECT CURRENT_TIMESTAMP, f.uuid, f.name
  FROM functions f
  WHERE f.active = 1;

END $$
DELIMITER ;