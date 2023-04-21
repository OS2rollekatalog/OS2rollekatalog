DROP VIEW IF EXISTS view_datatables_users;

GO

-- TODO: does not work as intended... multiple positions all point to the same (first?) OU
CREATE VIEW view_datatables_users AS (
  SELECT u.uuid AS uuid, u.name AS name, user_id AS user_id, 
    CONCAT(p.name, ' i ', o.name) AS title, 
    o.uuid AS orgunit_uuid,
    u.disabled AS disabled,
    d.name AS domain
  FROM users u 
  LEFT JOIN positions p ON p.user_uuid = u.uuid LEFT JOIN ous o ON o.uuid = p.ou_uuid
  JOIN domains d ON u.domain_id = d.id
  WHERE u.deleted = 0
);