ALTER TABLE ous ADD level NVARCHAR(64);
GO
UPDATE ous SET level = 'NONE';