INSERT INTO constraint_types(uuid, entity_id, name, ui_type) VALUES('d9bd772b-32b0-4a56-b616-34fa3b465c5d', 'http://digital-identity.dk/constraints/itsystem/1', 'It-system', 'REGEX');
INSERT INTO constraint_types(uuid, entity_id, name, ui_type) VALUES('49be31cf-a1c5-4be1-bb96-73e693cce3ef', 'http://digital-identity.dk/constraints/orgunit/1', 'Enhed', 'REGEX');

SELECT id INTO @rc_it_systems_id FROM it_systems WHERE identifier = 'RoleCatalogue';
SELECT id INTO @rc_system_roles_assigner_id FROM system_roles WHERE identifier = 'http://rollekatalog.dk/assigner';

SELECT id INTO @rc_orgenhed_id FROM constraint_types WHERE entity_id = 'http://digital-identity.dk/constraints/orgunit/1';
SELECT id INTO @rc_itystem_id FROM constraint_types WHERE entity_id = 'http://digital-identity.dk/constraints/itsystem/1';

INSERT INTO system_role_supported_constraints (system_role_id, constraint_type_id, mandatory) VALUES (@rc_system_roles_assigner_id, @rc_orgenhed_id, false);
INSERT INTO system_role_supported_constraints (system_role_id, constraint_type_id, mandatory) VALUES (@rc_system_roles_assigner_id, @rc_itystem_id, false);
