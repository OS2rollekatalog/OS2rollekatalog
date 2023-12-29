
ALTER TABLE history_role_assignments ADD notify_by_email_if_manual_system BIT NOT NULL DEFAULT 1;
ALTER TABLE user_roles_mapping ADD notify_by_email_if_manual_system BIT NOT NULL DEFAULT 1;
