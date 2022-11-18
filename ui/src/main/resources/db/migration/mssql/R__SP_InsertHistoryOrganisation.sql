-- Stored procedure for populating history_kle_assignments table
-- should only be called once per day like this
--
-- EXEC SP_InsertHistoryOrganisation;
 
CREATE OR ALTER PROC SP_InsertHistoryOrganisation 
AS
BEGIN
  INSERT INTO history_ous (dato, ou_uuid, ou_name, ou_parent_uuid, ou_manager_uuid)
  SELECT CURRENT_TIMESTAMP, o.uuid, o.name, o.parent_uuid, o.manager
  FROM ous o
  WHERE o.active = 1;
  
  INSERT INTO history_ous_users (history_ous_id, user_uuid, title_uuid)
  SELECT ho.id, u.uuid, p.title_uuid
  FROM history_ous ho
  JOIN positions p ON p.ou_uuid = ho.ou_uuid
  JOIN users u ON u.uuid = p.user_uuid
  WHERE ho.dato = CAST(CURRENT_TIMESTAMP AS DATE)
    AND u.deleted = 0;
  
  INSERT INTO history_users (dato, user_uuid, user_ext_uuid, user_name, user_user_id, user_active)
  SELECT CURRENT_TIMESTAMP, u.uuid, u.ext_uuid, u.name, u.user_id, IIF(u.disabled = 1, 0, 1)
  FROM users u WHERE u.deleted = 0;
  
  INSERT INTO history_managers (dato, user_uuid, user_name)
  SELECT DISTINCT CURRENT_TIMESTAMP, o.manager, u.name
  FROM ous o
  LEFT JOIN users u ON u.uuid = o.manager
  WHERE o.manager IS NOT NULL AND o.active = 1;

  INSERT INTO history_titles (dato, title_uuid, title_name)
  SELECT CURRENT_TIMESTAMP, t.uuid, t.name
  FROM titles t
  WHERE t.active = 1;
  
END
GO
