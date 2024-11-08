ALTER TABLE user_roles ADD extra_sensitive_role BIT DEFAULT 0 NOT NULL
GO
ALTER TABLE history_user_roles ADD extra_sensitive_role BIT NULL
GO
exec sp_rename 'attestation_run.super_sensitive', extra_sensitive, 'COLUMN'
go

