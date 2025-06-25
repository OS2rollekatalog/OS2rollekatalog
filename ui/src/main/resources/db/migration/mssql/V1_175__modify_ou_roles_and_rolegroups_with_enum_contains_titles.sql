-- Drop default constraint on ou_roles.contains_titles
DECLARE @sql NVARCHAR(MAX) = '';
SELECT @sql = 'ALTER TABLE ou_roles DROP CONSTRAINT [' + dc.name + ']'
FROM sys.default_constraints dc
         JOIN sys.columns c ON c.default_object_id = dc.object_id
         JOIN sys.tables t ON t.object_id = c.object_id
WHERE t.name = 'ou_roles' AND c.name = 'contains_titles';

EXEC sp_executesql @sql;

-- Ændr kolonnen og tilføj ny constraint
ALTER TABLE ou_roles ALTER COLUMN contains_titles INT NOT NULL;
ALTER TABLE ou_roles ADD CONSTRAINT DF_ou_roles_contains_titles DEFAULT 0 FOR contains_titles;