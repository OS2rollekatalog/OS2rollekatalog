ALTER TABLE it_systems ADD COLUMN advis_email VARCHAR(255) NULL;
ALTER TABLE user_roles ADD COLUMN advis_email VARCHAR(255) NULL;
ALTER TABLE email_templates ADD COLUMN repeating_part MEDIUMTEXT NULL;
ALTER TABLE email_templates ADD COLUMN nested_repeating_part MEDIUMTEXT NULL;
