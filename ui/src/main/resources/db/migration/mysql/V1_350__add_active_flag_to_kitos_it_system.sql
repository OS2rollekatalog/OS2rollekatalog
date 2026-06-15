ALTER TABLE kitos_it_system ADD COLUMN active BOOLEAN DEFAULT TRUE;
ALTER TABLE kitos_it_system ADD COLUMN kitos_usage_uuid BINARY(16) NULL;