CREATE OR REPLACE VIEW view_datatables_users AS (
  SELECT u.uuid AS uuid, u.name AS name, user_id AS user_id, 
    GROUP_CONCAT(CONCAT(p.name, ' i ', o.name) SEPARATOR ';') AS title, 
    GROUP_CONCAT(o.uuid SEPARATOR ';') AS orgunit_uuid 
  FROM users u 
  JOIN positions p ON p.user_uuid = u.uuid JOIN ous o ON o.uuid = p.ou_uuid 
  WHERE u.active = 1
  GROUP BY u.uuid
);