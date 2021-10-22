INSERT INTO pending_kombit_updates (user_role_id, event_type)
  SELECT r.id, 'UPDATE'
  FROM user_roles r
  JOIN it_systems i ON i.id = r.it_system_id
  WHERE i.system_type = 'KOMBIT' AND r.uuid IS NULL;