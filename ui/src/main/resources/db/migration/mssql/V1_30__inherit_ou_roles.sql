ALTER TABLE ou_roles ADD inherit BIT NOT NULL DEFAULT 0;
ALTER TABLE ou_rolegroups ADD inherit BIT NOT NULL DEFAULT 0;
ALTER TABLE ou_roles ADD id BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1);
ALTER TABLE ou_rolegroups ADD id BIGINT NOT NULL PRIMARY KEY IDENTITY(1, 1);