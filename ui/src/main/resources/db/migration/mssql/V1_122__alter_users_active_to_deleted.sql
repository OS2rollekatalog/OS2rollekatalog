-- introduce a new deleted field
ALTER TABLE users ADD deleted BIT NOT NULL DEFAULT 0;

GO

-- update deleted field with reverse value of old active field
UPDATE users SET deleted = IIF(active = 1, 0, 1);

-- get rid of old active field
ALTER TABLE users DROP COLUMN active;

-- introduce a disabled field
ALTER TABLE users ADD disabled BIT NOT NULL DEFAULT 0;
