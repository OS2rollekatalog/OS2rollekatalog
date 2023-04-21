ALTER TABLE it_systems ADD COLUMN domain_id BIGINT NULL;
ALTER TABLE it_systems ADD CONSTRAINT fk_it_systems_domain FOREIGN KEY (domain_id) REFERENCES domains(id) ON DELETE CASCADE;
UPDATE it_systems SET domain_id = (SELECT id FROM domains WHERE name = 'Administrativt') WHERE system_type = 'AD';

DELETE FROM pending_ad_group_operations;
ALTER TABLE pending_ad_group_operations ADD COLUMN domain_id BIGINT NOT NULL;
ALTER TABLE pending_ad_group_operations ADD CONSTRAINT fk_pending_ad_group_operations_domain FOREIGN KEY (domain_id) REFERENCES domains(id) ON DELETE CASCADE;

DELETE FROM dirty_ad_groups;
ALTER TABLE dirty_ad_groups ADD COLUMN domain_id BIGINT NOT NULL;
ALTER TABLE dirty_ad_groups ADD CONSTRAINT fk_dirty_ad_groups_domain FOREIGN KEY (domain_id) REFERENCES domains(id) ON DELETE CASCADE;
