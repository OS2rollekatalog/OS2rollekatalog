ALTER TABLE ou_roles DROP CONSTRAINT DF__ou_roles__contai__4959E263;
ALTER TABLE ou_roles ALTER COLUMN contains_titles INT NOT NULL;
ALTER TABLE ou_roles ADD CONSTRAINT DF_ou_roles_contains_titles DEFAULT 0 FOR contains_titles;
GO
ALTER TABLE ou_rolegroups DROP CONSTRAINT DF__ou_rolegr__conta__4A4E069C;
ALTER TABLE ou_rolegroups ALTER COLUMN contains_titles INT NOT NULL;
ALTER TABLE ou_rolegroups ADD CONSTRAINT DF_ou_rolegroups_contains_titles DEFAULT 0 FOR contains_titles;
GO