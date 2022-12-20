ALTER TABLE positions ADD do_not_inherit BIT NOT NULL DEFAULT 0;

GO

UPDATE p
SET p.do_not_inherit = u.do_not_inherit
FROM positions p
INNER JOIN users u ON p.user_uuid = u.uuid;



DECLARE @DynSQL NVARCHAR(MAX);
SELECT @DynSQL = 'ALTER TABLE users DROP CONSTRAINT ' + df.name + ';'
FROM sys.default_constraints df
INNER JOIN sys.tables t ON df.parent_object_id = t.object_id
INNER JOIN sys.columns c ON df.parent_object_id = c.object_id AND df.parent_column_id = c.column_id
WHERE t.name = 'users' and c.name = 'do_not_inherit'

Exec(@DynSQL)

GO

ALTER TABLE users DROP COLUMN do_not_inherit;