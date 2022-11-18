DROP VIEW IF EXISTS view_datatables_attestations;

GO

CREATE VIEW view_datatables_attestations AS (
  SELECT o.uuid, o.name, m.name AS manager, o.last_attested_by, o.last_attested, o.next_attestation
  FROM ous o
  LEFT JOIN users m ON m.uuid = o.manager
  WHERE o.active = 1
);
