ALTER TABLE users ADD email NVARCHAR(255);
ALTER TABLE users ADD phone NVARCHAR(255);
ALTER TABLE users ADD last_updated DATETIME2 NULL;
ALTER TABLE ous ADD last_updated DATETIME2 NULL;