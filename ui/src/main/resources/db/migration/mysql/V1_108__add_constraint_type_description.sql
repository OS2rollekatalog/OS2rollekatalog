ALTER TABLE constraint_types ADD COLUMN description TEXT NULL;
DROP INDEX entity_id ON constraint_types;
