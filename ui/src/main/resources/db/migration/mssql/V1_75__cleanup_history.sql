DELETE FROM history_role_assignments WHERE assigned_through_type IN ('ORGUNIT', 'TITLE');
DELETE FROM history_kle_assignments WHERE dato < DATEADD(DAY, -7, GETDATE());
ALTER TABLE history_ous_users ADD id BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1);
ALTER TABLE history_ous_users ADD title_uuid NVARCHAR(36);
DELETE FROM history_title_role_assignments;
