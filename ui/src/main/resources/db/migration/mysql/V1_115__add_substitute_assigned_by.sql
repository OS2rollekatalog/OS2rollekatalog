ALTER TABLE users ADD COLUMN substitute_assigned_by VARCHAR(255) NULL AFTER manager_substitute;
ALTER TABLE users ADD COLUMN substitute_assigned_tts TIMESTAMP NULL AFTER substitute_assigned_by;