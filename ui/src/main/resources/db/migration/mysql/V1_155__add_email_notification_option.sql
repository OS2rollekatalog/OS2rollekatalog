
ALTER TABLE history_role_assignments ADD COLUMN notify_by_email_if_manual_system boolean null default true;
ALTER TABLE user_roles_mapping ADD COLUMN notify_by_email_if_manual_system boolean null default true;
