DROP VIEW IF EXISTS view_datatables_users;

GO

-- TODO: does not work as intended... multiple positions all point to the same (first?) OU
CREATE VIEW view_datatables_users AS (
  SELECT u.uuid AS uuid, u.name AS name, user_id AS user_id, 
    CONCAT(p.name, ' i ', o.name) AS title, 
    o.uuid AS orgunit_uuid,
    u.disabled AS disabled
  FROM users u 
  JOIN positions p ON p.user_uuid = u.uuid JOIN ous o ON o.uuid = p.ou_uuid 
  WHERE u.deleted = 0
);